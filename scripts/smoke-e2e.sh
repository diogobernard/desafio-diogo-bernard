#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
ROOT="$root"
source "$root/scripts/lib-compose-isolated.sh"
prepare_compose_isolated "$root" "smoke"
reports="app/build/reports/e2e"
mkdir -p "$reports"
export DB_PASSWORD="${DB_PASSWORD:-smoke-local-ficticia}"
export REPLICATION_PASSWORD="${REPLICATION_PASSWORD:-replica-smoke-ficticia}"
export LOCAL_CONSUMER_CONCURRENCY="${LOCAL_CONSUMER_CONCURRENCY:-2}"
trap cleanup_compose_isolated EXIT INT TERM

isolated_compose up -d --wait
for tentativa in $(seq 1 60); do
    readiness="$(curl --silent --fail --connect-timeout 2 --max-time 3 "http://localhost:$MANAGEMENT_PORT/actuator/health/readiness" 2>/dev/null || true)"
    if [[ "$readiness" == *'"status":"UP"'* ]]; then break; fi
    if [[ "$tentativa" == 60 ]]; then
        echo "Readiness não ficou UP; salvando logs em $reports/smoke-failure-app.log" >&2
        isolated_compose logs --no-color api worker > "$reports/smoke-failure-app.log" 2>&1 || true
        tail -n 100 "$reports/smoke-failure-app.log" >&2 || true
        exit 1
    fi
    sleep 2
done
chave="$(uuidgen | tr '[:upper:]' '[:lower:]')"
resposta="$(curl --silent --show-error --fail \
    -H 'Content-Type: application/json' -H 'X-Client-Id: smoke-local' -H "Idempotency-Key: $chave" \
    --data '{"recipient":{"person_type":"FISICA","document":"52998224725"},"items":[{"code":"SMOKE","description":"Teste local","unit_price":10,"quantity":1}],"freight_value":1,"addresses":[{"purpose":"ENTREGA","region":"SUDESTE"}]}' \
    "http://localhost:$API_PORT/issuance-requests")"
id="$(printf '%s' "$resposta" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
[[ -n "$id" ]] || { echo "POST não retornou id de solicitação" >&2; exit 1; }

for tentativa in $(seq 1 120); do
    if ! status="$(curl --silent --show-error --fail --connect-timeout 2 --max-time 5 \
        -H 'X-Client-Id: smoke-local' "http://localhost:$API_PORT/issuance-requests/$id")"; then
        echo "GET do smoke não respondeu; salvando logs em $reports/smoke-failure-app.log" >&2
        isolated_compose logs --no-color api worker > "$reports/smoke-failure-app.log" 2>&1 || true
        tail -n 100 "$reports/smoke-failure-app.log" >&2 || true
        exit 1
    fi
    if [[ "$status" == *'"status":"EMITIDA"'* ]]; then
        for notificacao_tentativa in $(seq 1 60); do
            notificacoes="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At \
                -c "SELECT count(*) FROM notafiscal.notificacao_recebida n JOIN notafiscal.nota_fiscal f USING (serie, numero) WHERE f.solicitacao_id='$id'")"
            [[ "$notificacoes" == 4 ]] && break
            sleep 1
        done
        [[ "$notificacoes" == 4 ]] || { echo "A emissão não chegou aos quatro consumidores" >&2; exit 1; }
        integridade="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At \
            -c "SELECT count(*) FROM notafiscal.nota_fiscal WHERE solicitacao_id='$id'")"
        [[ "$integridade" == 1 ]] || { echo "A solicitação não produziu exatamente uma nota" >&2; exit 1; }
        numeracao="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At \
            -c "SELECT (count(*)=max(numero) AND count(*)=count(DISTINCT numero))::text FROM notafiscal.nota_fiscal WHERE serie=1")"
        [[ "$numeracao" == true ]] || { echo "A numeração da série contém lacuna ou duplicidade" >&2; exit 1; }
        for componente in "API:$MANAGEMENT_PORT:notafiscal_solicitacoes_recebidas_total" \
            "worker:$WORKER_MANAGEMENT_PORT:notafiscal_notas_emitidas_total"; do
            IFS=: read -r nome porta metrica <<< "$componente"
            metricas="$(curl --silent --fail --max-time 5 "http://localhost:$porta/actuator/prometheus" || true)"
            [[ "$metricas" == *"$metrica"* ]] || { echo "O actuator do $nome não expôs $metrica" >&2; exit 1; }
        done
        echo "Smoke E2E-11 passou: API e worker separados emitiram uma nota, concluíram quatro notificações e expuseram métricas Prometheus."
        exit 0
    fi
    if [[ "$status" == *'"status":"FALHA"'* ]]; then echo "Emissão falhou no smoke E2E" >&2; exit 1; fi
    sleep 1
done
echo "Solicitação não chegou a EMITIDA dentro do timeout" >&2
exit 1
