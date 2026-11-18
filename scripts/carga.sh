#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
ROOT="$root"
source "$root/scripts/lib-compose-isolated.sh"
source "$root/scripts/lib-carga-coleta.sh"
source "$root/scripts/lib-carga-experimento.sh"
prepare_compose_isolated "$root" "carga"
export LOAD_SERVICOS_MEDIDOS="api worker"
export COMPOSE_PROFILES=replica
export DB_READ_URL="jdbc:postgresql://postgres-replica:5432/notafiscal?currentSchema=notafiscal"
reports="app/build/reports/load"
mkdir -p "$reports"
exportar_perfil_carga "$reports"
preparar_experimento_carga "$root" "$reports"
prazo_emissao="${EMISSION_TIMEOUT_SECONDS:-180}"
prazo_escoamento="${DRAIN_TIMEOUT_SECONDS:-300}"

trap 'parar_coleta; cleanup_compose_isolated' EXIT INT TERM

isolated_compose up -d --wait
aguardar_api_pronta
# Aquecimento separado das medições do Gatling.
aquecer_api_basico

if [[ -n "${LOAD_REQUESTS:-}" ]]; then
    quantidades=("$LOAD_REQUESTS")
else
    quantidades=(1000)
fi

# Aquece API, worker e consumidores com o próprio cenário, fora da medição, como no post e no consumo.
# LOAD_WARMUP_REQUESTS continua aceito como ajuste explícito (marca a rodada como experimento).
aquecimento_combinado="${LOAD_WARMUP_REQUESTS:-${LOAD_COMBINADO_AQUECIMENTO:-3000}}"
[[ "$aquecimento_combinado" =~ ^[0-9]+$ ]] || {
    echo "LOAD_COMBINADO_AQUECIMENTO deve ser um inteiro não negativo" >&2
    exit 1
}
printf 'aquecimento_combinado=%s\n' "$aquecimento_combinado" > "$reports/aquecimento-combinado.txt"
if (( aquecimento_combinado > 0 )); then
    aquecer_com_gatling "$aquecimento_combinado" "${quantidades[*]}" "$reports"
    # As notificações do aquecimento não podem entrar no lag nem no escoamento medidos.
    aguardar_escoamento "$prazo_escoamento" >> "$reports/aquecimento-combinado.txt"
    grep -q '^escoado=sim' "$reports/aquecimento-combinado.txt" || {
        echo "O aquecimento não escoou em ${prazo_escoamento} s; a medição seria contaminada" >&2
        exit 1
    }
fi

for quantidade in "${quantidades[@]}"; do
    cliente="carga-$quantidade"
    echo "Executando carga aberta de ${quantidade} solicitações a 1.000 req/s"
    inicio_coleta="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    iniciar_coleta "$quantidade"
    isolated_compose stats --no-stream > "$reports/recursos-${quantidade}.txt"
    ./gradlew :app:gatlingRun \
        --non-interactive --simulation com.itau.desafio_diogo_bernard.load.PostGetIssuanceLoadSimulation \
        -Dload.requests="$quantidade" -Dapi.baseUrl="http://localhost:$API_PORT"
    for tentativa in $(seq 1 "$prazo_emissao"); do
        pendentes="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At \
            -c "SELECT count(*) FROM notafiscal.solicitacao WHERE cliente_id='$cliente' AND status IN ('RECEBIDA','PROCESSANDO')")"
        if [[ "$pendentes" == "0" ]]; then break; fi
        sleep 1
    done
    aguardar_escoamento "$prazo_escoamento" > "$reports/escoamento-${quantidade}.txt"
    parar_coleta
    coletar_pools "$inicio_coleta" "$reports/pools-${quantidade}.txt"
    isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -F '|' \
        -c "SELECT state, count(*), max(extract(epoch FROM (now()-xact_start))*1000)::bigint FROM pg_stat_activity WHERE datname='notafiscal' AND pid <> pg_backend_pid() GROUP BY state ORDER BY state" \
        > "$reports/postgres-estados-${quantidade}.txt"
    isolated_compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server kafka:29092 --all-groups --describe \
        > "$reports/kafka-lag-${quantidade}.txt" 2>&1 || true
    isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -v ON_ERROR_STOP=1 \
        -c "WITH rodada AS (SELECT *, extract(epoch FROM (emitida_em-recebida_em))*1000 AS duracao_ms FROM notafiscal.solicitacao WHERE cliente_id='carga-${quantidade}') SELECT '${quantidade}' AS rodada, count(*) AS aceitas, count(*) FILTER (WHERE status='EMITIDA') AS emitidas, count(*) FILTER (WHERE status='FALHA') AS falhas, count(*) FILTER (WHERE status IN ('RECEBIDA','PROCESSANDO')) AS pendentes, round((percentile_cont(0.50) WITHIN GROUP (ORDER BY duracao_ms) FILTER (WHERE emitida_em IS NOT NULL))::numeric, 2) AS emissao_p50_ms, round((percentile_cont(0.95) WITHIN GROUP (ORDER BY duracao_ms) FILTER (WHERE emitida_em IS NOT NULL))::numeric, 2) AS emissao_p95_ms, round((percentile_cont(0.99) WITHIN GROUP (ORDER BY duracao_ms) FILTER (WHERE emitida_em IS NOT NULL))::numeric, 2) AS emissao_p99_ms FROM rodada" \
        > "$reports/rodada-${quantidade}.txt"
    isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -v ON_ERROR_STOP=1 \
        -v cliente="$cliente" < scripts/verificar-numeracao.sql > "$reports/numeracao-${quantidade}.txt"
    coletar_metricas_prometheus "$reports/metricas-${quantidade}.txt"
    isolated_compose stats --no-stream > "$reports/recursos-${quantidade}-fim.txt"
    gatling_report="$(ultimo_relatorio_gatling PostGetIssuanceLoadSimulation)"
    if [[ -z "$gatling_report" ]]; then
        echo "Relatório Gatling da rodada ${quantidade} não foi encontrado" >&2
        exit 1
    fi
    python3 scripts/atualizar-relatorio-carga.py \
        --requests "$quantidade" \
        --gatling-report "$gatling_report" \
        --reports "$reports" \
        --document "$documento_relatorio"
done

if ! isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -v ON_ERROR_STOP=1 \
    > "$reports/ambiente-final.txt" <<'SQL'
SELECT now() AS coletado_em,
       (SELECT count(*) FROM notafiscal.outbox WHERE publicado_em IS NULL) AS outbox_pendente,
       (SELECT count(*) FROM notafiscal.nota_fiscal) AS notas_emitidas;
SQL
then
    echo "Coleta final de outbox/notas indisponível." > "$reports/ambiente-final.txt"
fi
if [[ "${LOAD_JFR:-0}" == 1 ]]; then
    isolated_compose stop -t 120 api > /dev/null 2>&1 || true
    ls -l "$reports/jfr/api.jfr" > "$reports/jfr/api-jfr-arquivo.txt" 2>&1 || true
fi
echo "Dados brutos salvos em $reports e relatórios HTML em app/build/reports/gatling."
