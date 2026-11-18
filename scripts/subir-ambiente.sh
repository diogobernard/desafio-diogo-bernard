#!/usr/bin/env bash
# Sobe o ambiente local completo (PostgreSQL, Kafka, API e worker) e espera API e worker ficarem prontos.
#
# Uso: bash scripts/subir-ambiente.sh [opções]
#   --replica     inclui a réplica de leitura do PostgreSQL e aponta as leituras para ela
#   --verificar   depois de subir, emite uma nota de teste e acompanha até EMITIDA
#   --sem-build   reutiliza a imagem já construída da aplicação
#   --parar       encerra o ambiente e mantém os dados do banco
#   --limpar      encerra o ambiente e apaga o volume do banco
#   -h, --help    mostra esta ajuda
#
# Variáveis opcionais: SUBIR_TIMEOUT_S (padrão 240) limita a espera pelo readiness de cada processo.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

API_URL="http://localhost:8080"
GERENCIAMENTO_API_URL="http://localhost:8081"
GERENCIAMENTO_WORKER_URL="http://localhost:8082"
URL_LEITURA_REPLICA="jdbc:postgresql://postgres-replica:5432/notafiscal?currentSchema=notafiscal"
CLIENTE_VERIFICACAO="cliente-local"
tempo_limite="${SUBIR_TIMEOUT_S:-240}"
usar_replica=false
construir=true
verificar=false
acao="subir"

uso() { sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; }
falhar() { echo "ERRO: $*" >&2; exit 1; }
etapa() { printf '\n==> %s\n' "$*"; }

for argumento in "$@"; do
    case "$argumento" in
        --replica) usar_replica=true ;;
        --verificar) verificar=true ;;
        --sem-build) construir=false ;;
        --parar) acao="parar" ;;
        --limpar) acao="limpar" ;;
        -h|--help) uso; exit 0 ;;
        *) uso >&2; falhar "opção desconhecida: $argumento" ;;
    esac
done

compose() { docker compose "$@"; }

# Os perfis opcionais entram no down para que a réplica e o Grafana também sejam encerrados.
# O Compose exige as senhas só para interpretar o arquivo; sem .env, valores descartáveis bastam.
encerrar() {
    if [[ ! -f .env ]]; then
        export DB_PASSWORD="${DB_PASSWORD:-nao-utilizada}"
        export REPLICATION_PASSWORD="${REPLICATION_PASSWORD:-nao-utilizada}"
    fi
    docker compose --profile replica --profile observabilidade down --remove-orphans "$@"
}

verificar_prerequisitos() {
    command -v docker >/dev/null 2>&1 || falhar "Docker não encontrado."
    docker compose version >/dev/null 2>&1 || falhar "Docker Compose v2 não encontrado (comando 'docker compose')."
    docker info >/dev/null 2>&1 || falhar "O daemon do Docker não está em execução."
    command -v curl >/dev/null 2>&1 || falhar "curl não encontrado."
}

gerar_segredo() {
    openssl rand -hex 16 2>/dev/null || python3 -c 'import secrets; print(secrets.token_hex(16))'
}

gerar_uuid() {
    uuidgen 2>/dev/null | tr '[:upper:]' '[:lower:]' || python3 -c 'import uuid; print(uuid.uuid4())'
}

nome_projeto() {
    printf '%s' "${COMPOSE_PROJECT_NAME:-$(basename "$root")}" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_-'
}

volume_banco_existe() {
    [[ -n "$(docker volume ls -q \
        --filter "label=com.docker.compose.project=$(nome_projeto)" \
        --filter "label=com.docker.compose.volume=postgres-data")" ]]
}

valor_configurado() {
    local chave="$1"
    if [[ -n "${!chave:-}" ]]; then printf '%s' "${!chave}"; return; fi
    [[ -f .env ]] && sed -n "s/^${chave}=//p" .env | tail -n 1
}

criar_env() {
    if volume_banco_existe; then
        falhar "O volume do banco já existe, mas o .env não. Restaure o .env com a senha usada na criação \
do volume ou apague os dados com: bash scripts/subir-ambiente.sh --limpar"
    fi
    cp .env.example .env
    chmod 600 .env
    sed -i.bak \
        -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(gerar_segredo)|" \
        -e "s|^REPLICATION_PASSWORD=.*|REPLICATION_PASSWORD=$(gerar_segredo)|" .env
    rm -f .env.bak
    echo ".env criado a partir do .env.example com senhas locais aleatórias (arquivo ignorado pelo Git)."
}

validar_env() {
    local chave valor
    for chave in DB_PASSWORD REPLICATION_PASSWORD; do
        valor="$(valor_configurado "$chave")"
        if [[ -z "$valor" || "$valor" == \<*\> ]]; then
            falhar "Defina $chave no .env com um valor local (o atual está vazio ou é um placeholder)."
        fi
    done
    if [[ "$usar_replica" == false && "$(valor_configurado DB_READ_URL)" == *postgres-replica* ]]; then
        falhar "DB_READ_URL aponta para a réplica. Use --replica ou remova DB_READ_URL do .env."
    fi
}

esperar_readiness() {
    local servico="$1" url="$2/actuator/health/readiness" limite=$((SECONDS + tempo_limite)) resposta
    etapa "Aguardando o readiness do $servico em $url"
    until resposta="$(curl -sS --max-time 3 "$url" 2>/dev/null)" && [[ "$resposta" == *'"status":"UP"'* ]]; do
        if (( SECONDS >= limite )); then
            compose logs --no-color --tail 80 "$servico" >&2 || true
            falhar "O $servico não ficou pronto em ${tempo_limite}s. Veja os logs acima."
        fi
        sleep 2
    done
}

subir() {
    local perfis=() build=()
    [[ "$usar_replica" == true ]] && perfis=(--profile replica)
    [[ "$construir" == true ]] && build=(--build)
    if [[ "$usar_replica" == true ]]; then
        export DB_READ_URL="$URL_LEITURA_REPLICA"
    fi
    etapa "Subindo PostgreSQL e Kafka (aguardando health checks)"
    local infraestrutura=(postgres kafka)
    [[ "$usar_replica" == true ]] && infraestrutura+=(postgres-replica)
    compose ${perfis[@]+"${perfis[@]}"} up -d --wait "${infraestrutura[@]}"
    if [[ "$construir" == true ]]; then etapa "Construindo e subindo API e worker"; else etapa "Subindo API e worker"; fi
    compose ${perfis[@]+"${perfis[@]}"} up -d ${build[@]+"${build[@]}"} api worker
    esperar_readiness api "$GERENCIAMENTO_API_URL"
    esperar_readiness worker "$GERENCIAMENTO_WORKER_URL"
}

campo_json() {
    sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"
}

exibir_json() {
    if command -v jq >/dev/null 2>&1; then jq .; else python3 -m json.tool 2>/dev/null || cat; fi
}

verificar_emissao() {
    etapa "Emitindo uma nota de teste (cliente $CLIENTE_VERIFICACAO, dados fictícios)"
    local pedido resposta id status link
    pedido='{"recipient":{"person_type":"FISICA","document":"52998224725"},'
    pedido+='"items":[{"code":"SKU-001","description":"Teclado","unit_price":250.00,"quantity":2},'
    pedido+='{"code":"SKU-002","description":"Mouse","unit_price":99.90,"quantity":1}],'
    pedido+='"freight_value":30.00,"addresses":[{"purpose":"ENTREGA","region":"SUDESTE"}]}'
    resposta="$(curl -sS --fail-with-body -H 'Content-Type: application/json' \
        -H "X-Client-Id: $CLIENTE_VERIFICACAO" -H "Idempotency-Key: $(gerar_uuid)" \
        --data "$pedido" "$API_URL/issuance-requests")" || falhar "O POST de verificação falhou: $resposta"
    id="$(printf '%s' "$resposta" | campo_json id)"
    [[ -n "$id" ]] || falhar "O POST não devolveu o id da solicitação."
    echo "202 Accepted: solicitação $id"
    for _ in $(seq 1 60); do
        resposta="$(curl -sS -H "X-Client-Id: $CLIENTE_VERIFICACAO" "$API_URL/issuance-requests/$id")" \
            || falhar "A consulta da solicitação $id falhou."
        status="$(printf '%s' "$resposta" | campo_json status)"
        case "$status" in
            EMITIDA) break ;;
            FALHA) falhar "A solicitação terminou em FALHA: $resposta" ;;
        esac
        sleep 1
    done
    [[ "$status" == EMITIDA ]] || falhar "A solicitação não chegou a EMITIDA em 60 s (último estado: $status)."
    link="$(printf '%s' "$resposta" | campo_json nota)"
    echo "Solicitação EMITIDA. Nota em $API_URL$link:"
    curl -sS -H "X-Client-Id: $CLIENTE_VERIFICACAO" "$API_URL$link" | exibir_json
}

resumo() {
    cat <<EOF

Ambiente pronto.
  API ........................ $API_URL
  Actuator da API ............ $GERENCIAMENTO_API_URL/actuator/health/readiness e /actuator/prometheus
  Actuator do worker ......... $GERENCIAMENTO_WORKER_URL/actuator/health/readiness e /actuator/prometheus
  PostgreSQL (primária) ...... localhost:5432 (usuário notafiscal, senha no .env)
EOF
    [[ "$usar_replica" == true ]] && echo "  PostgreSQL (réplica) ....... localhost:5433"
    cat <<EOF
  Kafka ...................... localhost:9092

Logs:     docker compose logs -f api worker
Encerrar: bash scripts/subir-ambiente.sh --parar   (ou --limpar para apagar os dados)
Exemplos de requisições: seção "Testar manualmente" do README.md
EOF
}

verificar_prerequisitos
case "$acao" in
    parar)
        encerrar
        exit 0
        ;;
    limpar)
        encerrar --volumes
        exit 0
        ;;
esac
[[ -f .env ]] || criar_env
validar_env
subir
[[ "$verificar" == true ]] && verificar_emissao
resumo
