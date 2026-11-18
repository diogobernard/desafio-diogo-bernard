#!/usr/bin/env bash
# E2E-14 mede só os dois GETs. Sobem PostgreSQL, réplica e API; Kafka e worker ficam desligados.
# Fluxo: alimenta o banco uma vez (5k solicitações emitidas e as 5k notas, por scripts/seed-consulta.sql), aquece a JVM
# e mede 1k, 5k e 10k usuários sobre essa mesma base; o feeder reaproveita as notas em círculo quando há mais usuários.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"; cd "$root"; ROOT="$root"
source scripts/lib-compose-isolated.sh; source scripts/lib-carga-coleta.sh; source scripts/lib-carga-experimento.sh
export LOAD_SERVICOS_MEDIDOS=api LOAD_API_SEM_KAFKA=1 COLETA_KAFKA=0
export COMPOSE_PROFILES=replica
export DB_READ_URL="jdbc:postgresql://postgres-replica:5432/notafiscal?currentSchema=notafiscal"
prepare_compose_isolated "$root" "consulta"
reports=app/build/reports/load; mkdir -p "$reports"; exportar_perfil_carga "$reports"; preparar_experimento_carga "$root" "$reports"
base_csv="$reports/consulta-base.csv"
aquecimento_csv="$reports/consulta-aquecimento.csv"
arquivar_evidencias_consulta() {
  local n="$1" destino="$root/tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-$n-$(date -u +%Y%m%dT%H%M%SZ)"
  mkdir -p "$destino"
  for arquivo in instancia.txt experimento.txt ingresso-config.txt cenario-consulta.txt aquecimento-consulta.txt time-wait-consulta-$n.txt listen-consulta-$n.txt pools-consulta-$n.txt fallback-antes-consulta-$n.txt fallback-depois-consulta-$n.txt fallback-resumo-consulta-$n.txt recursos-consulta-$n.txt lag-replica-consulta-$n-antes.txt lag-replica-consulta-$n-depois.txt lag-replica-consulta-inicial.txt; do
    [[ -f "$reports/$arquivo" ]] && cp "$reports/$arquivo" "$destino/"
  done
  cp "$reports"/{atividade-consulta-$n.tsv,ingresso-fila-consulta-$n.tsv,carga-host-consulta-$n.tsv} "$destino/" 2>/dev/null || true
  cp "$reports/carga-host-resumo-consulta-$n.txt" "$base_csv" "$destino/" 2>/dev/null || true
  for log in "$reports"/gc/api-*.log; do [[ -f "$log" ]] && cp "$log" "$destino/"; done
  local relatorio
  relatorio="$(ultimo_relatorio_gatling GetQueryLoadSimulation)"
  [[ -z "$relatorio" ]] || cp -R "$relatorio" "$destino/gatling"
}
resumir_fallbacks_consulta() {
  local fonte="$1" destino="$2" motivo valor
  : > "$destino"
  for motivo in recente negado ultimo_numero; do
    valor="$(awk -v chave="notafiscal_leitura_fallback_total{motivo=\"$motivo\"}" 'index($0, chave) > 0 { valor = $NF } END { if (valor != "") print valor }' "$fonte")"
    [[ -n "$valor" ]] || valor=0
    printf 'motivo=%s total=%s\n' "$motivo" "$valor" >> "$destino"
  done
}
registrar_lag_replica() {
  local destino="$1"
  isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -F '|' -c \
    "SELECT application_name, state, sync_state, coalesce(pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn), 0)::bigint FROM pg_stat_replication ORDER BY application_name" \
    > "$destino"
}
aguardar_replica_sincronizada() {
  local tentativa sincronizada
  for tentativa in $(seq 1 120); do
    sincronizada="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -c \
      "SELECT (count(*) > 0 AND coalesce(max(pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn)), 0) = 0)::text FROM pg_stat_replication")"
    [[ "$sincronizada" == true ]] && return 0
    sleep 1
  done
  echo "Réplica não alcançou lag zero em 120 s" >&2
  return 1
}
trap 'parar_coleta; cleanup_compose_isolated' EXIT INT TERM
# Alimenta o banco uma única vez, antes de qualquer consulta, e exporta id/número/cliente para o feeder do Gatling.
alimentar_banco() {
  local n="${LOAD_CONSULTA_NOTAS:-5000}" cliente="consulta" aquecimento="consulta-aquecimento"
  [[ "$n" =~ ^[1-9][0-9]*$ ]] || { echo "LOAD_CONSULTA_NOTAS deve ser um inteiro positivo" >&2; exit 1; }
  echo "Alimentando o banco: $n solicitações emitidas e as $n notas fiscais"
  isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -q -v ON_ERROR_STOP=1 \
    -v cliente="$cliente" -v quantidade="$n" -v serie=1 < "$root/scripts/seed-consulta.sql" > /dev/null
  isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -F ',' \
    -c "SELECT id, nota_numero, cliente_id FROM notafiscal.solicitacao WHERE cliente_id='$cliente' ORDER BY recebida_em" > "$base_csv"
  isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -q -v ON_ERROR_STOP=1 \
    -v cliente="$aquecimento" -v quantidade=3000 -v serie=1 < "$root/scripts/seed-consulta.sql" > /dev/null
  isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -F ',' \
    -c "SELECT id, nota_numero, cliente_id FROM notafiscal.solicitacao WHERE cliente_id='$aquecimento' ORDER BY recebida_em" > "$aquecimento_csv"
  # Adiciona cabeçalho sem guardar payload nem documento.
  sed -i.bak '1i\
id,numero,cliente' "$base_csv"; rm -f "$base_csv.bak"
  sed -i.bak '1i\
id,numero,cliente' "$aquecimento_csv"; rm -f "$aquecimento_csv.bak"
  printf 'preparacao=sql_uma_vez\nnotas=%s\naquecimento_notas=3000\nreplica=ativa\nkafka=nao_iniciado\nworker=nao_iniciado\npost=nenhum\n' "$n" > "$reports/cenario-consulta.txt"
}
# Aquece a JVM (JIT, pool, planos de consulta e serializadores) com uma base separada antes da medição.
# A carga sobe em rampa (de 10 a 1.000 usuários/s) e segue em patamar: aquecer a 1.000 usuários/s com a JVM fria
# forma uma fila de pedidos esperando conexão que esgota o heap da API. LOAD_CONSULTA_AQUECIMENTO=0 desliga.
aquecer_jvm() {
  local rampa="${LOAD_CONSULTA_RAMPA:-30}" patamar="${LOAD_CONSULTA_PATAMAR:-10}" consulta_anterior consulta_aquecimento
  printf 'aquecimento=%s\naquecimento_rampa_s=%s\naquecimento_patamar_s=%s\n' "${LOAD_CONSULTA_AQUECIMENTO:-1}" "$rampa" "$patamar" > "$reports/aquecimento-consulta.txt"
  [[ "${LOAD_CONSULTA_AQUECIMENTO:-1}" != 0 ]] || return 0
  echo "Aquecendo a JVM: rampa de ${rampa}s até 1.000 usuários/s e patamar de ${patamar}s, fora da medição"
  consulta_anterior="$(ultimo_relatorio_gatling GetQueryLoadSimulation)"
  ./gradlew :app:gatlingRun --non-interactive --simulation com.itau.desafio_diogo_bernard.load.GetQueryLoadSimulation \
    -Dload.feeder="$root/$aquecimento_csv" -Dapi.baseUrl="http://localhost:$API_PORT" \
    -Dload.aquecimento=true -Dload.rampa.segundos="$rampa" -Dload.patamar.segundos="$patamar" \
    > "$reports/aquecimento-consulta.log" 2>&1 || true
  consulta_aquecimento="$(ultimo_relatorio_gatling GetQueryLoadSimulation)"
  [[ "$consulta_aquecimento" == "$consulta_anterior" ]] || rm -rf "$consulta_aquecimento"
  # O aquecimento não pode derrubar a API: sem isso, a medição seguinte rodaria contra um processo morto.
  aguardar_api_pronta
}
# Primária, réplica e API. --no-deps evita subir Kafka; DB_READ_URL direciona as leituras à réplica.
isolated_compose up -d --wait --no-deps postgres postgres-replica api; aguardar_api_pronta
quantidades=(1000 5000 10000); [[ -z "${LOAD_REQUESTS:-}" ]] || quantidades=("$LOAD_REQUESTS")
alimentar_banco
aguardar_replica_sincronizada
registrar_lag_replica "$reports/lag-replica-consulta-inicial.txt"
aquecer_jvm
falhou=0
for n in "${quantidades[@]}"; do
  # As portas efêmeras do cliente ficam em TIME_WAIT por 30 s após cada rajada; esperar evita esgotá-las (ver lib).
  aguardar_time_wait_baixo 1000 90 | sed 's/^/antes /' | tee "$reports/time-wait-consulta-$n.txt"
  curl --silent --fail --max-time 10 "http://localhost:$MANAGEMENT_PORT/actuator/prometheus" | grep 'notafiscal_leitura_fallback' > "$reports/fallback-antes-consulta-$n.txt" || true
  registrar_lag_replica "$reports/lag-replica-consulta-$n-antes.txt"
  inicio="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; listen_antes="$(contadores_listen)"; iniciar_coleta "consulta-$n"
  # Uma asserção reprovada não interrompe o cenário: a evidência do volume é arquivada e o script falha no fim.
  ./gradlew :app:gatlingRun --non-interactive --simulation com.itau.desafio_diogo_bernard.load.GetQueryLoadSimulation \
    -Dload.requests="$n" -Dload.feeder="$root/$base_csv" -Dapi.baseUrl="http://localhost:$API_PORT" || falhou=1
  printf 'depois time_wait=%s\n' "$(contar_time_wait)" >> "$reports/time-wait-consulta-$n.txt"
  printf 'antes %s\ndepois %s\n' "$listen_antes" "$(contadores_listen)" > "$reports/listen-consulta-$n.txt"
  parar_coleta; coletar_pools "$inicio" "$reports/pools-consulta-$n.txt"
  curl --silent --fail --max-time 10 "http://localhost:$MANAGEMENT_PORT/actuator/prometheus" | grep -E 'notafiscal_leitura_(fallback|404_falso)' > "$reports/fallback-depois-consulta-$n.txt" || true
  registrar_lag_replica "$reports/lag-replica-consulta-$n-depois.txt"
  resumir_fallbacks_consulta "$reports/fallback-depois-consulta-$n.txt" "$reports/fallback-resumo-consulta-$n.txt"
  isolated_compose stats --no-stream > "$reports/recursos-consulta-$n.txt"
  arquivar_evidencias_consulta "$n"
done
(( falhou == 0 )) || { echo "Uma ou mais rajadas reprovaram nas asserções do Gatling" >&2; exit 1; }
