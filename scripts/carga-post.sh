#!/usr/bin/env bash
# Carga do POST isolado: só o POST recebe tráfego e o worker fica parado durante as rajadas.
# Meta: 100% dos POST aceitos e latência máxima de cada POST até LOAD_POST_MAX_MS (padrão 3000 ms).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
ROOT="$root"
source "$root/scripts/lib-compose-isolated.sh"
source "$root/scripts/lib-carga-coleta.sh"
source "$root/scripts/lib-carga-experimento.sh"
prepare_compose_isolated "$root" "carga"
export LOAD_SERVICOS_MEDIDOS=api
reports="app/build/reports/load"
mkdir -p "$reports"
exportar_perfil_carga "$reports"
preparar_experimento_carga "$root" "$reports"
latencia_maxima="${LOAD_POST_MAX_MS:-3000}"
# true simula clientes atrás de um gateway, que mantém conexões abertas; false abre uma conexão por POST.
compartilhar_conexoes="${LOAD_COMPARTILHAR_CONEXOES:-false}"
# A medição é da instância em regime: com 2 CPUs a JVM fria não sustenta 1.000 POST/s enquanto compila o código.
aquecimento_post="${LOAD_POST_AQUECIMENTO:-3000}"
printf 'latencia_maxima_post_ms=%s\nconexoes_compartilhadas=%s\naquecimento_post=%s\n' \
    "$latencia_maxima" "$compartilhar_conexoes" "$aquecimento_post" > "$reports/meta-post.txt"

trap 'parar_coleta; cleanup_compose_isolated' EXIT INT TERM

isolated_compose up -d --wait
aguardar_api_pronta
aquecer_api_basico

if [[ -n "${LOAD_REQUESTS:-}" ]]; then
    quantidades=("$LOAD_REQUESTS")
else
    quantidades=(1000 5000 10000)
fi
rodadas="${LOAD_POST_RODADAS:-2}"
[[ "$rodadas" =~ ^[1-9][0-9]*$ ]] || { echo "LOAD_POST_RODADAS deve ser um inteiro positivo" >&2; exit 1; }
# O consumo não faz parte deste teste: sem worker, a API não divide a máquina com a emissão.
isolated_compose stop worker > /dev/null
if (( aquecimento_post > 0 )); then
    aquecer_com_gatling "$aquecimento_post" "${quantidades[*]}" "$reports" PostIngressLoadSimulation
fi

reprovadas=""
for rodada in $(seq 1 "$rodadas"); do
for quantidade in "${quantidades[@]}"; do
    echo "Executando POST isolado: ${quantidade} solicitações a 1.000 req/s (meta: máximo de ${latencia_maxima} ms)"
    inicio_coleta="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    iniciar_coleta "post-$quantidade"
    listen_antes="$(contadores_listen)"
    relatorio_anterior="$(ultimo_relatorio_gatling PostIngressLoadSimulation)"
    resultado=0
    ./gradlew :app:gatlingRun \
        --non-interactive --simulation com.itau.desafio_diogo_bernard.load.PostIngressLoadSimulation \
        -Dload.requests="$quantidade" -Dapi.baseUrl="http://localhost:$API_PORT" \
        -Dload.client="carga-$quantidade-r$rodada" \
        -Dload.post.maxMs="$latencia_maxima" -Dload.compartilharConexoes="$compartilhar_conexoes" || resultado=$?
    coletar_metricas_prometheus "$reports/metricas-post-${quantidade}.txt"
    printf 'antes %s\ndepois %s\n' "$listen_antes" "$(contadores_listen)" > "$reports/listen-post-${quantidade}.txt"
    parar_coleta
    coletar_pools "$inicio_coleta" "$reports/pools-post-${quantidade}.txt"
    isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -v ON_ERROR_STOP=1 -At -F '|' \
        -v cliente="carga-$quantidade-r$rodada" < scripts/verificar-ingresso.sql > "$reports/consistencia-post-${quantidade}.txt"
    gatling_report="$(ultimo_relatorio_gatling PostIngressLoadSimulation)"
    if [[ -z "$gatling_report" || "$gatling_report" == "$relatorio_anterior" ]]; then
        echo "A rodada de POST de ${quantidade} não gerou um novo relatório Gatling" >&2
        exit 1
    fi
    python3 scripts/atualizar-relatorio-carga.py --cenario post \
        --requests "$quantidade" \
        --gatling-report "$gatling_report" \
        --reports "$reports" \
        --document "$documento_relatorio"
    if (( resultado != 0 )); then
        reprovadas="$reprovadas ${quantidade}(rodada-${rodada})"
        {
            printf 'resultado_gatling=%s\nvolume=%s\nrodada=%s\n' "$resultado" "$quantidade" "$rodada"
            cat "$reports/instancia.txt" "$reports/carga-host-resumo-post-$quantidade.txt" \
                "$reports/pools-post-$quantidade.txt" "$reports/listen-post-$quantidade.txt" \
                "$reports/consistencia-post-$quantidade.txt" "$reports/metricas-post-$quantidade.txt" 2>/dev/null || true
        } > "$reports/diagnostico-post-$quantidade-r$rodada.txt"
    fi
done
evidencias="$root/tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-$rodada-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$evidencias"
cp "$reports"/meta-post.txt "$reports"/instancia.txt "$reports"/experimento.txt \
    "$reports"/ingresso-config.txt "$reports"/amostragem-traces.txt "$evidencias/" 2>/dev/null || true
cp "$reports"/*-post-*.txt "$reports"/*-post-*.tsv "$reports"/carga-host-post-*.tsv \
    "$reports"/carga-host-resumo-post-*.txt "$reports"/gc/api-*.log "$evidencias/" 2>/dev/null || true
done

if [[ "${LOAD_JFR:-0}" == 1 ]]; then
    isolated_compose stop -t 120 api > /dev/null 2>&1 || true
    ls -l "$reports/jfr/api.jfr" > "$reports/jfr/api-jfr-arquivo.txt" 2>&1 || true
fi
echo "Dados brutos salvos em $reports e relatórios HTML em app/build/reports/gatling."
if [[ -n "$reprovadas" ]]; then
    echo "Meta do POST (100% aceito e máximo de ${latencia_maxima} ms) não atendida em:${reprovadas}" >&2
    exit 1
fi
