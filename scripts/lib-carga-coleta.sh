#!/usr/bin/env bash
# Coletores compartilhados pelos cenários de carga. Requer lib-compose-isolated.sh já carregado,
# a variável $reports e as portas exportadas por prepare_compose_isolated.

aguardar_api_pronta() {
    local tentativa estado
    for tentativa in $(seq 1 60); do
        estado="$(curl --silent --fail --connect-timeout 2 --max-time 3 \
            "http://localhost:$MANAGEMENT_PORT/actuator/health/readiness" 2>/dev/null || true)"
        if [[ "$estado" == *'"status":"UP"'* ]]; then return; fi
        sleep 2
    done
    echo "Readiness não ficou UP; salvando logs do app em $reports/readiness-failure-app.log" >&2
    isolated_compose logs --no-color api worker > "$reports/readiness-failure-app.log" 2>&1 || true
    tail -n 100 "$reports/readiness-failure-app.log" >&2 || true
    return 1
}

carga_host_atual() {
    if [[ -r /proc/loadavg ]]; then
        awk '{print $1}' /proc/loadavg
    else
        sysctl -n vm.loadavg 2>/dev/null | tr -d '{}' | awk '{print $1}'
    fi
}

avisar_carga_host() {
    local carga ncpu
    carga="$(carga_host_atual)"
    ncpu="$(sysctl -n hw.ncpu 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1)"
    if awk -v carga="$carga" -v ncpu="$ncpu" 'BEGIN {exit !(carga > ncpu)}'; then
        echo "AVISO: carga inicial do host ($carga) acima dos $ncpu núcleos; a rodada pode refletir contenção." >&2
    fi
    printf 'carga_host_inicio=%s\n nucleos_host=%s\n' "$carga" "$ncpu" >> "$reports/host-carga.txt"
}

iniciar_amostragem_host() {
    local arquivo="$1" sinal="$2"
    avisar_carga_host
    while [[ -e "$sinal" ]]; do
        printf '%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(carga_host_atual)" >> "$arquivo"
        sleep 2
    done
}

resumir_carga_host() {
    local arquivo="$1" resumo="$2" pico inicio fim
    pico="$(awk -F '\t' '$2 ~ /^[0-9.]+$/ {if ($2 > max) max=$2} END {if (max == "") print "indisponivel"; else print max}' "$arquivo")"
    inicio="$(head -n 1 "$arquivo" | cut -f2)"
    fim="$(tail -n 1 "$arquivo" | cut -f2)"
    printf 'carga_host_inicio=%s\ncarga_host_fim=%s\ncarga_host_pico=%s\n' "$inicio" "$fim" "$pico" > "$resumo"
}

aquecer_api_basico() {
    local rodada
    for rodada in 1 2 3; do
        curl --silent --show-error --fail --connect-timeout 2 --max-time 10 -o /dev/null \
            -H 'Content-Type: application/json' -H 'X-Client-Id: carga-aquecimento' \
            -H "Idempotency-Key: $(uuidgen | tr '[:upper:]' '[:lower:]')" \
            --data '{"recipient":{"person_type":"FISICA","document":"52998224725"},"items":[{"code":"WARMUP","description":"Aquecimento","unit_price":1,"quantity":1}],"freight_value":0,"addresses":[{"purpose":"ENTREGA","region":"SUDESTE"}]}' \
            "http://localhost:$API_PORT/issuance-requests"
    done
}

total_lag_kafka() {
    local saida
    saida="$(isolated_compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server kafka:29092 --all-groups --describe 2>/dev/null)" || { echo indisponivel; return; }
    awk '$6 ~ /^[0-9]+$/ { total += $6 } END { print total + 0 }' <<< "$saida"
}

aguardar_escoamento() {
    local prazo="$1" inicio="$SECONDS" outbox lag
    while true; do
        outbox="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At \
            -c "SELECT count(*) FROM notafiscal.outbox WHERE publicado_em IS NULL" 2>/dev/null || echo indisponivel)"
        lag="$(total_lag_kafka)"
        if [[ "$outbox" == "0" && "$lag" == "0" ]]; then
            printf 'escoado=sim tempo_s=%s prazo_s=%s\n' "$((SECONDS - inicio))" "$prazo"
            return
        fi
        if (( SECONDS - inicio >= prazo )); then
            printf 'escoado=nao prazo_s=%s outbox_restante=%s lag_restante=%s\n' "$prazo" "$outbox" "$lag"
            return
        fi
        sleep 2
    done
}

monitorar_atividade() {
    local arquivo="$1" sinal="$2" instante estado recursos
    while [[ -e "$sinal" ]]; do
        instante="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        estado="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At -F '|' \
            -c "SELECT (SELECT count(*) FROM pg_stat_activity WHERE datname='notafiscal' AND pid <> pg_backend_pid()), (SELECT count(*) FROM pg_stat_activity WHERE datname='notafiscal' AND state='idle in transaction'), coalesce((SELECT max(extract(epoch FROM (now()-xact_start))*1000)::bigint FROM pg_stat_activity WHERE datname='notafiscal'),0), coalesce((SELECT max(extract(epoch FROM (now()-xact_start))*1000)::bigint FROM pg_stat_activity WHERE datname='notafiscal' AND state='idle in transaction'),0), (SELECT count(*) FROM pg_stat_activity WHERE datname='notafiscal' AND state='active'), (SELECT count(*) FROM notafiscal.outbox WHERE publicado_em IS NULL), (SELECT count(*) FROM notafiscal.solicitacao WHERE status IN ('RECEBIDA','PROCESSANDO'))" 2>/dev/null || true)"
        recursos="$(isolated_compose stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}' 2>/dev/null || true)"
        printf '%s\t%s\t%s\n' "$instante" "${estado:-indisponivel}" "${recursos:-indisponivel}" >> "$arquivo"
        sleep 2
    done
}

amostrar_fila() {
    local arquivo="$1" sinal="$2" tamanho
    while [[ -e "$sinal" ]]; do
        tamanho="$(curl --silent --connect-timeout 1 --max-time 2 \
            "http://localhost:$MANAGEMENT_PORT/actuator/metrics/notafiscal.ingresso.fila.tamanho" 2>/dev/null \
            | grep -o '"value":[0-9.eE+-]*' | head -n 1 | cut -d: -f2 || true)"
        printf '%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${tamanho:-indisponivel}" >> "$arquivo"
        sleep 1
    done
}

amostrar_lag_kafka() {
    local arquivo="$1" sinal="$2" lag
    while [[ -e "$sinal" ]]; do
        lag="$(total_lag_kafka)"
        printf '%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$lag" >> "$arquivo"
        sleep 2
    done
}

# Cada usuário do Gatling abre uma conexão nova, e o cliente fica com a porta em TIME_WAIT por 2*MSL (30 s no macOS).
# A faixa de portas efêmeras tem 16.384 portas: rajadas seguidas a 1.000 conexões/s podem esgotá-la e produzir
# "Connection reset by peer (connect failed)" no cliente, sem relação com a API.
contar_time_wait() {
    netstat -an -p tcp 2>/dev/null | awk '/TIME_WAIT/ { total++ } END { print total + 0 }'
}

aguardar_time_wait_baixo() {
    local limite="$1" prazo="$2" inicio="$SECONDS" atual
    atual="$(contar_time_wait)"
    while (( atual > limite && SECONDS - inicio < prazo )); do
        sleep 2
        atual="$(contar_time_wait)"
    done
    printf 'time_wait=%s limite=%s espera_s=%s\n' "$atual" "$limite" "$((SECONDS - inicio))"
}

# Não amostre durante a rajada: percorrer milhares de sockets a cada segundo coincidiu com quedas de vazão (de ~1.000 para
# 169 req/s) e com saltos da carga do host. Meça só antes e depois.

iniciar_coleta() {
    local rotulo="$1"
    arquivo_sinal="$reports/.coletar-${rotulo}"
    : > "$reports/atividade-${rotulo}.tsv"
    : > "$reports/ingresso-fila-${rotulo}.tsv"
    touch "$arquivo_sinal"
    : > "$reports/carga-host-$rotulo.tsv"
    monitorar_atividade "$reports/atividade-${rotulo}.tsv" "$arquivo_sinal" &
    coletor_pid=$!
    amostrar_fila "$reports/ingresso-fila-${rotulo}.tsv" "$arquivo_sinal" &
    fila_pid=$!
    iniciar_amostragem_host "$reports/carga-host-$rotulo.tsv" "$arquivo_sinal" &
    host_pid=$!
    # COLETA_KAFKA=0 nos cenários sem Kafka (consulta): não há lag a amostrar nem contêiner a consultar.
    if [[ "${COLETA_KAFKA:-1}" == 1 ]]; then
        : > "$reports/kafka-lag-${rotulo}.tsv"
        amostrar_lag_kafka "$reports/kafka-lag-$rotulo.tsv" "$arquivo_sinal" &
        lag_pid=$!
    fi
}

parar_coleta() {
    if [[ -n "${coletor_pid:-}" ]]; then
        rm -f "${arquivo_sinal:-}"
        wait "$coletor_pid" 2>/dev/null || true
        coletor_pid=""
    fi
    if [[ -n "${fila_pid:-}" ]]; then
        wait "$fila_pid" 2>/dev/null || true
        fila_pid=""
    fi
    if [[ -n "${host_pid:-}" ]]; then
        wait "$host_pid" 2>/dev/null || true
        host_pid=""
        resumir_carga_host "$reports/carga-host-${arquivo_sinal##*.coletar-}.tsv" \
            "$reports/carga-host-resumo-${arquivo_sinal##*.coletar-}.txt"
    fi
    if [[ -n "${lag_pid:-}" ]]; then
        wait "$lag_pid" 2>/dev/null || true
        lag_pid=""
    fi
}

coletar_pools() {
    isolated_compose logs --no-color --since "$1" api worker | grep 'Pool Hikari:' > "$2" || true
}

coletar_metricas_prometheus() {
    if curl --silent --show-error --fail --connect-timeout 2 --max-time 5 \
        "http://localhost:$MANAGEMENT_PORT/actuator/prometheus" \
        | grep -E 'notafiscal_(emissao_duracao|numeracao_espera_lock|leitura_fallback|leitura_404_falso|ingresso_(lote|rejeitadas|fila))' \
        > "$1"; then
        return
    fi
    echo "Coleta Prometheus indisponível nesta rodada (sem resposta ou sem séries correspondentes)." > "$1"
}

contadores_listen() {
    isolated_compose exec -T api cat /proc/net/netstat 2>/dev/null | awk '
        /^TcpExt:/ && !cabecalho { for (i = 2; i <= NF; i++) nome[i] = $i; cabecalho = 1; next }
        /^TcpExt:/ { for (i = 2; i <= NF; i++) if (nome[i] ~ /^Listen(Overflows|Drops)$/) printf "%s=%s ", nome[i], $i; print "" }'
}

ultimo_relatorio_gatling() {
    local prefixo
    prefixo="$(tr '[:upper:]' '[:lower:]' <<< "$1")"
    [[ -d app/build/reports/gatling ]] || return 0
    find app/build/reports/gatling -mindepth 1 -maxdepth 1 -type d -name "$prefixo-*" -print | sort | tail -n 1
}
