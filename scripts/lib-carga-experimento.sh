#!/usr/bin/env bash
# Chaves opt-in de experimentos de carga. Sem nenhuma delas o ambiente é o do docker-compose.yml.
#   LOAD_JFR=1              grava o JFR da API em $reports/jfr/api.jfr (dump ao parar a API)
#   LOAD_INSTANCIA_CPUS=4   limite de CPUs para o serviço medido (padrão 4, limitado pelo Docker)
#   LOAD_API_CPUS=4         substitui o limite de CPUs da API (experimento explícito)
#   LOAD_API_MEMORY=3g      substitui o limite de memória da API
#   LOAD_API_JVM_OPTS="..." substitui as opções da JVM da API (o -jar é mantido)
#   LOAD_WARMUP_REQUESTS=N  substitui o aquecimento padrão do combinado (3.000, LOAD_COMBINADO_AQUECIMENTO)
#   LOAD_EXPERIMENTO=1      marca a rodada como exploratória mesmo sem as chaves acima
#   LOAD_API_SEM_KAFKA=1    a API sobe sem Kafka (readiness só com banco); usado pelo cenário de consulta
# Rodadas com qualquer chave gravam o resultado numa cópia do relatório, sem alterar a tabela versionada.

exportar_perfil_carga() {
    local reports="$1"
    # Valores fictícios apenas para execução local; não são credenciais compartilhadas.
    export DB_PASSWORD="${DB_PASSWORD:-carga-local-ficticia}"
    export REPLICATION_PASSWORD="${REPLICATION_PASSWORD:-replica-local-ficticia}"
    export LOCAL_CONSUMER_CONCURRENCY="${LOAD_CONSUMER_CONCURRENCY:-${LOCAL_CONSUMER_CONCURRENCY:-10}}"
    # A amostragem reduzida evita medir o custo do tracing completo; é registrada no relatório.
    export MANAGEMENT_TRACING_SAMPLING_PROBABILITY="${MANAGEMENT_TRACING_SAMPLING_PROBABILITY:-0.1}"
    printf 'amostragem_traces=%s\n' "$MANAGEMENT_TRACING_SAMPLING_PROBABILITY" > "$reports/amostragem-traces.txt"
    # Perfil de carga: a fila absorve a rajada inteira de 10k e a espera fica abaixo do timeout padrão de 60 s do Gatling.
    export NOTA_FISCAL_INGRESSO_CAPACIDADE="${NOTA_FISCAL_INGRESSO_CAPACIDADE:-10000}"
    export NOTA_FISCAL_INGRESSO_ESPERA_MAXIMA="${NOTA_FISCAL_INGRESSO_ESPERA_MAXIMA:-50s}"
    export SERVER_TOMCAT_MAX_CONNECTIONS="${SERVER_TOMCAT_MAX_CONNECTIONS:-12000}"
    # Fila de conexões pendentes do socket (limitada pelo somaxconn de 4096): com o padrão de 100 do Tomcat, a rajada
    # descarta SYN e o cliente só retransmite após 1, 3, 7 ou 15 s.
    export SERVER_TOMCAT_ACCEPT_COUNT="${SERVER_TOMCAT_ACCEPT_COUNT:-4096}"
    printf 'ingresso_capacidade=%s\ningresso_espera_maxima=%s\ntomcat_max_connections=%s\ningresso_escritores=%s\ningresso_lote_maximo=%s\ntomcat_accept_count=%s\nconsumidor_concorrencia=%s\nconsumidor_max_em_voo=%s\n' \
        "$NOTA_FISCAL_INGRESSO_CAPACIDADE" "$NOTA_FISCAL_INGRESSO_ESPERA_MAXIMA" "$SERVER_TOMCAT_MAX_CONNECTIONS" \
        "${NOTA_FISCAL_INGRESSO_ESCRITORES:-2}" "${NOTA_FISCAL_INGRESSO_LOTE_MAXIMO:-100}" \
        "${SERVER_TOMCAT_ACCEPT_COUNT:-100}" "$LOCAL_CONSUMER_CONCURRENCY" "${LOAD_CONSUMER_MAX_IN_FLIGHT:-200}" > "$reports/ingresso-config.txt"
}

preparar_perfil_instancia() {
    local reports="$1" solicitadas disponiveis efetivas
    solicitadas="${LOAD_INSTANCIA_CPUS:-4}"
    [[ "$solicitadas" =~ ^[1-9][0-9]*([.][0-9]+)?$ ]] || { echo "LOAD_INSTANCIA_CPUS deve ser positivo" >&2; return 1; }
    disponiveis="$(docker info --format '{{.NCPU}}')"
    [[ "$disponiveis" =~ ^[1-9][0-9]*$ ]] || { echo "Não foi possível consultar as CPUs disponíveis no Docker" >&2; return 1; }
    efetivas="$(python3 - "$solicitadas" "$disponiveis" <<'PY'
import sys
print(min(float(sys.argv[1]), int(sys.argv[2])))
PY
    )"
    export LOAD_INSTANCIA_CPUS_EFETIVAS="$efetivas"
    printf 'cpus_docker=%s\ncpus_instancia_solicitadas=%s\ncpus_instancia_efetivas=%s\nservicos_dedicados=%s\n' \
        "$disponiveis" "$solicitadas" "$efetivas" "$(tr ' ' ',' <<< "${LOAD_SERVICOS_MEDIDOS:-api worker}")" \
        > "$reports/instancia.txt"
}

opcoes_jvm_em_yaml() {
    local opcao
    for opcao in $1; do printf ', "%s"' "$opcao"; done
}

experimento_ativo() {
    [[ "${LOAD_EXPERIMENTO:-0}" == 1 || "${LOAD_JFR:-0}" == 1 || -n "${LOAD_WARMUP_REQUESTS:-}" ]] ||
        [[ -n "${LOAD_API_CPUS:-}" || -n "${LOAD_API_MEMORY:-}" || -n "${LOAD_API_JVM_OPTS:-}" ]]
}

registrar_experimento() {
    printf 'jfr=%s\ncpus_api_experimento=%s\ncpus_instancia=%s\nmemoria_api=%s\nopcoes_jvm_api=%s\naquecimento_requisicoes=%s\n' \
        "${LOAD_JFR:-0}" "${LOAD_API_CPUS:-padrao}" "${LOAD_INSTANCIA_CPUS_EFETIVAS:-4}" \
        "${LOAD_API_MEMORY:-padrao}" "${LOAD_API_JVM_OPTS:-padrao}" "${LOAD_WARMUP_REQUESTS:-3}" > "$2/experimento.txt"
}

gerar_override_api() {
    local servicos="${3:-api worker}" cpus jvm_opcoes
    echo 'services:'
    for servico in $servicos; do
    cpus="${LOAD_INSTANCIA_CPUS_EFETIVAS:-4}"
    [[ "$servico" != api || -z "${LOAD_API_CPUS:-}" ]] || cpus="$LOAD_API_CPUS"
    jvm_opcoes="-Xlog:gc*,safepoint:file=/gc/${servico}-%p.log:time,uptime,level,tags:filecount=2,filesize=64m"
    [[ "$servico" != api || "${LOAD_JFR:-0}" != 1 ]] || jvm_opcoes+=" -XX:StartFlightRecording=filename=/gc/api.jfr,settings=profile,dumponexit=true,maxsize=500m"
    echo "  $servico:"
    echo '    deploy:'
    echo '      resources:'
    echo '        limits:'
    echo "          cpus: \"$cpus\""
    echo '    environment:'
    echo "      JAVA_TOOL_OPTIONS: \"$jvm_opcoes\""
    if [[ "$servico" == api && "${LOAD_API_SEM_KAFKA:-0}" == 1 ]]; then
        # Cenário sem Kafka: a readiness não depende do broker e a API não tenta criar tópicos ao subir.
        echo '      MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE: "readinessState,db"'
        echo '      SPRING_KAFKA_ADMIN_AUTO_CREATE: "false"'
    fi
    if [[ "$servico" == worker ]]; then
        for consumidor in ESTOQUE REGISTRO ENTREGA FINANCEIRO; do
            echo "      NOTA_FISCAL_CONSUMIDORES_${consumidor}_MAX_EM_VOO: \"${LOAD_CONSUMER_MAX_IN_FLIGHT:-200}\""
        done
    fi
    echo '    volumes:'
    echo "      - $1/$2/gc:/gc"
    if [[ "$servico" == api ]]; then
    if [[ -n "${LOAD_API_JVM_OPTS:-}" ]]; then
        printf '    entrypoint: ["java"%s, "-jar", "/app/app.jar"]\n' "$(opcoes_jvm_em_yaml "$LOAD_API_JVM_OPTS")"
    fi
        if [[ -n "${LOAD_API_MEMORY:-}" ]]; then echo "          memory: \"$LOAD_API_MEMORY\""; fi
    fi
    done
}

preparar_experimento_carga() {
    local root="$1" reports="$2"
    documento_relatorio="docs/carga/relatorio.md"
    registrar_experimento "$root" "$reports"
    preparar_perfil_instancia "$reports"
    mkdir -p "$root/$reports/gc"
    rm -f "$root/$reports/gc"/*
    COMPOSE_OVERRIDE_PATH="$(mktemp "${TMPDIR:-/tmp}/notafiscal-compose-carga.XXXXXX")"
    gerar_override_api "$root" "$reports" "${LOAD_SERVICOS_MEDIDOS:-api worker}" > "$COMPOSE_OVERRIDE_PATH"
    if experimento_ativo; then
        documento_relatorio="$reports/relatorio-experimento.md"
        cp "$root/docs/carga/relatorio.md" "$root/$documento_relatorio"
    fi
}

aquecer_com_gatling() {
    local requisicoes="$1" medida="$2" reports="$3" simulacao="${4:-PostGetIssuanceLoadSimulation}"
    if [[ " $medida " == *" $requisicoes "* ]]; then
        echo "O aquecimento deve usar um volume diferente dos volumes medidos ($medida)" >&2
        return 1
    fi
    # O aquecimento não é medição: a asserção de latência do POST fica relaxada e uma reprovação não interrompe a carga.
    local anterior relatorio
    anterior="$(ultimo_relatorio_gatling "$simulacao")"
    ./gradlew :app:gatlingRun \
        --non-interactive --simulation "com.itau.desafio_diogo_bernard.load.$simulacao" \
        -Dload.requests="$requisicoes" -Dapi.baseUrl="http://localhost:$API_PORT" -Dload.post.maxMs=600000 \
        > "$reports/aquecimento-gatling.log" 2>&1 || true
    # O relatório do aquecimento é descartado para não se misturar aos medidos.
    relatorio="$(ultimo_relatorio_gatling "$simulacao")"
    [[ "$relatorio" == "$anterior" ]] || rm -rf "$relatorio"
    local tentativa pendentes
    for tentativa in $(seq 1 120); do
        if [[ "$simulacao" != PostGetIssuanceLoadSimulation ]]; then break; fi
        pendentes="$(isolated_compose exec -T postgres psql -U notafiscal -d notafiscal -At \
            -c "SELECT count(*) FROM notafiscal.solicitacao WHERE cliente_id='carga-$requisicoes' AND status IN ('RECEBIDA','PROCESSANDO')")"
        if [[ "$pendentes" == "0" ]]; then break; fi
        sleep 1
    done
    sleep "${LOAD_WARMUP_PAUSA:-20}"
}
