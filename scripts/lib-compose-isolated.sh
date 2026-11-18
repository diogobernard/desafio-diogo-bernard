#!/usr/bin/env bash

prepare_compose_isolated() {
    local root="$1"
    COMPOSE_PROJECT="notafiscal-${2}-$$"
    COMPOSE_FILE_PATH="$(mktemp "${TMPDIR:-/tmp}/notafiscal-compose.XXXXXX")"
    local selected
    selected="$(python3 - <<'PY'
import socket

def free_port(start, stop, used):
    for port in range(start, stop):
        if port in used:
            continue
        sock = socket.socket()
        try:
            sock.bind(("127.0.0.1", port))
            return port
        except OSError:
            pass
        finally:
            sock.close()
    raise SystemExit(f"Não há porta livre na faixa {start}-{stop - 1}")

api = free_port(3000, 3100, set())
management = free_port(3000, 3100, {api})
worker_management = free_port(3100, 3200, {api, management})
db = free_port(3500, 3600, {api})
replica = free_port(3600, 3700, {api, db})
kafka = free_port(3900, 4000, {api, db, replica})
print(api, db, replica, kafka, management, worker_management)
PY
    )"
    read -r API_PORT DB_PORT REPLICA_PORT KAFKA_PORT MANAGEMENT_PORT WORKER_MANAGEMENT_PORT <<< "$selected"
    python3 - "$root/docker-compose.yml" "$COMPOSE_FILE_PATH" "$API_PORT" "$DB_PORT" "$REPLICA_PORT" "$KAFKA_PORT" \
        "$MANAGEMENT_PORT" "$WORKER_MANAGEMENT_PORT" <<'PY'
from pathlib import Path
import sys

source, target, api, db, replica, kafka, management, worker_management = sys.argv[1:]
text = Path(source).read_text()
for original, replacement in (
    ('"8080:8080"', f'"{api}:8080"'),
    ('"8081:8081"', f'"{management}:8081"'),
    ('"8082:8081"', f'"{worker_management}:8081"'),
    ('"5432:5432"', f'"{db}:5432"'),
    ('"5433:5432"', f'"{replica}:5432"'),
    ('"9092:9092"', f'"{kafka}:9092"'),
):
    if original not in text:
        raise SystemExit(f"Mapeamento Compose esperado não encontrado: {original}")
    text = text.replace(original, replacement, 1)
Path(target).write_text(text)
PY
    export API_PORT DB_PORT REPLICA_PORT KAFKA_PORT MANAGEMENT_PORT WORKER_MANAGEMENT_PORT
}

isolated_compose() {
    local override=()
    if [[ -n "${COMPOSE_OVERRIDE_PATH:-}" ]]; then override=(-f "$COMPOSE_OVERRIDE_PATH"); fi
    docker compose --project-directory "$ROOT" -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE_PATH" \
        ${override[@]+"${override[@]}"} "$@"
}

cleanup_compose_isolated() {
    isolated_compose down --remove-orphans --volumes >/dev/null 2>&1 || true
    rm -f "$COMPOSE_FILE_PATH" "${COMPOSE_OVERRIDE_PATH:-}"
}
