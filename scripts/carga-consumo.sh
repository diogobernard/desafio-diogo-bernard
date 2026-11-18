#!/usr/bin/env bash
# E2E-13 mede o escoamento do worker a partir de backlogs com API já aquecida.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"; cd "$root"; ROOT="$root"
source scripts/lib-compose-isolated.sh; source scripts/lib-carga-coleta.sh; source scripts/lib-carga-experimento.sh
# O worker é o serviço medido. A API também recebe o perfil dedicado (4 CPUs) porque monta os
# backlogs a 1.000 req/s antes da medição; com 2 CPUs e o host ocupado, ela recusava conexões.
# Durante a medição a API fica sem tráfego, então ela não disputa CPU com o worker.
export LOAD_SERVICOS_MEDIDOS="api worker"
prepare_compose_isolated "$root" "consumo"
reports=app/build/reports/load; mkdir -p "$reports"; exportar_perfil_carga "$reports"; preparar_experimento_carga "$root" "$reports"
prazo="${EMISSION_TIMEOUT_SECONDS:-900}"
session_timeout="${LOAD_WORKER_SESSION_TIMEOUT_SECONDS:-45}"
finalizar_consumo() {
  parar_coleta
  cleanup_compose_isolated
}
arquivar_evidencias_consumo() {
  local n="$1" destino="$root/tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-$n-$(date -u +%Y%m%dT%H%M%SZ)"
  mkdir -p "$destino"
  cp "$reports"/{instancia,experimento,ingresso-config,backlog-consumo-$n,vazao-consumo-$n,escoamento-consumo-$n,resumo-consumo-$n,consistencia-consumo-$n,metricas-consumo-$n,recursos-consumo-$n,pools-consumo-$n}.txt "$destino/"
  cp "$reports"/{atividade-consumo-$n,kafka-lag-consumo-$n,ingresso-fila-consumo-$n,carga-host-consumo-$n}.tsv "$destino/"
  cp "$reports/carga-host-resumo-consumo-$n.txt" "$destino/"
  cp "$reports"/dlq-*-consumo-$n.txt "$destino/"
  for log in "$reports"/gc/worker-*.log; do [[ -f "$log" ]] && cp "$log" "$destino/"; done
}
trap finalizar_consumo EXIT INT TERM
isolated_compose up -d --wait; aguardar_api_pronta; aquecer_api_basico
quantidades=(1000 5000 10000); [[ -z "${LOAD_REQUESTS:-}" ]] || quantidades=("$LOAD_REQUESTS")

criar_backlog() {
  local n="$1" cliente="$2"
  ./gradlew :app:gatlingRun --non-interactive --simulation com.itau.desafio_diogo_bernard.load.PostIngressLoadSimulation \
    -Dload.requests="$n" -Dload.client="$cliente" -Dload.post.maxMs=600000 -Dapi.baseUrl="http://localhost:$API_PORT"
  local total
  total="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -c "SELECT count(*) FROM notafiscal.solicitacao WHERE cliente_id='$cliente'")"
  [[ "$total" == "$n" ]] || { echo "Backlog incompleto: esperado $n, recebido $total" >&2; return 1; }
}

aguardar_grupos_kafka_prontos() {
  local log
  for _ in $(seq 1 120); do
    log="$(isolated_compose logs --no-color worker 2>/dev/null || true)"
    if grep -q 'notafiscal-api: partitions assigned:' <<< "$log" \
      && grep -q 'notafiscal.estoque.v1: partitions assigned:' <<< "$log" \
      && grep -q 'notafiscal.registro.v1: partitions assigned:' <<< "$log" \
      && grep -q 'notafiscal.entrega.v1: partitions assigned:' <<< "$log" \
      && grep -q 'notafiscal.financeiro.v1: partitions assigned:' <<< "$log"; then return 0; fi
    sleep 1
  done
  echo "Consumidores Kafka não concluíram a atribuição antes da pausa" >&2
  return 1
}

for n in "${quantidades[@]}"; do
  cliente="consumo-$n"; echo "Preparando aquecimento de 3000 e consumo isolado de $n solicitações"
  # Não pausar durante o rebalance inicial: o worker precisa estar associado aos tópicos
  # antes do backlog, ou os consumidores podem ficar sem atribuição após o unpause.
  aguardar_grupos_kafka_prontos
  isolated_compose pause worker >/dev/null
  pausado_em="$SECONDS"
  criar_backlog 3000 "consumo-aquecimento-$n"
  pausa_aquecimento="$((SECONDS-pausado_em))"
  (( pausa_aquecimento < session_timeout )) || { echo "Pausa do worker no aquecimento (${pausa_aquecimento}s) excedeu session.timeout.ms (${session_timeout}s)" >&2; exit 1; }
  isolated_compose unpause worker >/dev/null
  for _ in $(seq 1 900); do
    pend="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -c "SELECT count(*) FROM notafiscal.solicitacao WHERE cliente_id='consumo-aquecimento-$n' AND status <> 'EMITIDA'")"
    [[ "$pend" == 0 ]] && break; sleep 1
  done
  [[ "${pend:-1}" == 0 ]] || { echo "Aquecimento do worker não escoou" >&2; exit 1; }
  for _ in $(seq 1 300); do
    aquecidas="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -c "SELECT count(*) FROM notafiscal.notificacao_recebida nr JOIN notafiscal.outbox o USING(evento_id) WHERE o.chave IN (SELECT id::text FROM notafiscal.solicitacao WHERE cliente_id='consumo-aquecimento-$n')")"
    [[ "$aquecidas" == 12000 ]] && break; sleep 1
  done
  [[ "${aquecidas:-0}" == 12000 ]] || { echo "Notificações de aquecimento incompletas: ${aquecidas:-0}/12000" >&2; exit 1; }
  isolated_compose pause worker >/dev/null
  pausado_em="$SECONDS"
  criar_backlog "$n" "$cliente"
  eventos="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -c "SELECT count(*) FROM notafiscal.outbox WHERE topico='notafiscal.solicitacoes.v1' AND chave IN (SELECT id::text FROM notafiscal.solicitacao WHERE cliente_id='$cliente')")"
  [[ "$eventos" == "$n" ]] || { echo "Outbox pré-medição inconsistente: $eventos/$n" >&2; exit 1; }
  pausa_s="$((SECONDS-pausado_em))"
  (( pausa_s < session_timeout )) || { echo "Pausa do worker (${pausa_s}s) excedeu session.timeout.ms (${session_timeout}s)" >&2; exit 1; }
  printf 'solicitacoes=%s\neventos_outbox=%s\npausa_worker_s=%s\nsession_timeout_s=%s\nworker_pausado_antes_medicao=sim\n' \
    "$n" "$eventos" "$pausa_s" "$session_timeout" > "$reports/backlog-consumo-$n.txt"
  inicio="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; iniciar_coleta "consumo-$n"; instante_inicio="$SECONDS"
  isolated_compose unpause worker >/dev/null
  for tentativa in $(seq 1 "$prazo"); do
    pend="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -c "SELECT count(*) FROM notafiscal.solicitacao WHERE cliente_id='$cliente' AND status <> 'EMITIDA'")"
    [[ "$pend" == 0 ]] && break; sleep 1
  done
  ate_emitir="$((SECONDS-instante_inicio))"
  (( ate_emitir > 0 )) || ate_emitir=1
  printf 'vazao_notas_por_s=%.2f\n' "$(awk -v n="$n" -v s="$ate_emitir" 'BEGIN {print n/s}')" > "$reports/vazao-consumo-$n.txt"
  await_lag=300
  for tentativa in $(seq 1 "$await_lag"); do
    notificadas="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -c "SELECT count(*) FROM notafiscal.notificacao_recebida n JOIN notafiscal.outbox o USING(evento_id) WHERE o.chave IN (SELECT id::text FROM notafiscal.solicitacao WHERE cliente_id='$cliente')")"
    [[ "$notificadas" == "$((n*4))" ]] && break; sleep 1
  done
  ate_notificar="$((SECONDS-instante_inicio))"
  aguardar_escoamento "$await_lag" > "$reports/escoamento-consumo-$n.txt"
  grep -q 'escoado=sim' "$reports/escoamento-consumo-$n.txt" || {
    echo "Outbox ou lag Kafka não zerou no prazo para $cliente" >&2; exit 1;
  }
  parar_coleta; coletar_pools "$inicio" "$reports/pools-consumo-$n.txt"
  if curl --silent --show-error --fail --connect-timeout 2 --max-time 5 \
    "http://localhost:$WORKER_MANAGEMENT_PORT/actuator/prometheus" \
    | grep -E 'notafiscal_(emissao_duracao|numeracao_espera_lock)' > "$reports/metricas-consumo-$n.txt"; then
    :
  else
    echo "Métricas de emissão e espera de numeração indisponíveis no worker." > "$reports/metricas-consumo-$n.txt"
  fi
  if ! grep -q 'notafiscal_emissao_duracao_seconds_count' "$reports/metricas-consumo-$n.txt" \
    || ! grep -q 'notafiscal_numeracao_espera_lock_seconds_count' "$reports/metricas-consumo-$n.txt"; then
    echo "Timers de emissão/lock não foram expostos pelo worker; veja $WORKER_MANAGEMENT_PORT." >&2
    exit 1
  fi
  isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -v ON_ERROR_STOP=1 -v cliente="$cliente" < scripts/verificar-consumo.sql > "$reports/consistencia-consumo-$n.txt"
  awk -F'|' -v n="$n" '$1=="solicitacoes" && $2!=n {falha=1} $1=="emitidas" && $2!=n {falha=1} $1=="eventos_nota" && $2!=n {falha=1} $1=="notificacoes" && $2!=4*n {falha=1} $1 ~ /ausentes|duplicados|repetidos|duplicidades|lacunas/ && $2!=0 {falha=1} END {exit falha}' \
    "$reports/consistencia-consumo-$n.txt" || { echo "Consistência de consumo falhou para $cliente" >&2; exit 1; }
  [[ "$pend" == 0 && "$notificadas" == "$((n*4))" ]] || { echo "Consumo incompleto: emitidas=$n pendentes=$pend notificações=$notificadas" >&2; exit 1; }
  isolated_compose stats --no-stream > "$reports/recursos-consumo-$n.txt"
  isolated_compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 --all-groups --describe > "$reports/kafka-lag-consumo-$n.tsv" 2>&1 || true
  for consumidor in estoque registro entrega financeiro; do
    isolated_compose exec -T kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:29092 \
      --topic "notafiscal.notas.v1.$consumidor.dlq" > "$reports/dlq-$consumidor-consumo-$n.txt" 2>&1 || true
    if awk -F: '$NF ~ /^[0-9]+$/ && $NF != 0 {nao_vazia=1} END {exit !nao_vazia}' "$reports/dlq-$consumidor-consumo-$n.txt"; then
      echo "DLQ de $consumidor não está vazia" >&2; exit 1
    fi
  done
  printf 'solicitacoes=%s\npendentes=%s\nnotificacoes=%s\ntempo_ate_emitir_s=%s\ntempo_ate_notificar_s=%s\n' \
    "$n" "${pend:-indisponivel}" "${notificadas:-indisponivel}" "$ate_emitir" "$ate_notificar" > "$reports/resumo-consumo-$n.txt"
  arquivar_evidencias_consumo "$n"
done
