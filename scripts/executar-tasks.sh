#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tasks_root="$repo_root/tasks"

usage() {
  cat <<'EOF'
Uso: scripts/executar-tasks.sh [--prd slug]

Executa sequencialmente as tarefas pendentes de tasks/prd-<slug>/tasks.md,
abrindo uma sessão nova do Codex para cada tarefa.

Se --prd for omitido, o script exige que exista exatamente um PRD em tasks/.
EOF
}

prd_slug=""
while (($#)); do
  case "$1" in
    --prd)
      if (($# < 2)) || [[ -z "$2" ]]; then
        echo "Erro: --prd precisa de um slug." >&2
        usage >&2
        exit 2
      fi
      prd_slug="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Argumento desconhecido: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$prd_slug" ]]; then
  shopt -s nullglob
  prd_dirs=("$tasks_root"/prd-*)
  shopt -u nullglob
  if ((${#prd_dirs[@]} != 1)); then
    echo "Erro: informe --prd slug; encontrados ${#prd_dirs[@]} diretórios tasks/prd-*." >&2
    exit 2
  fi
  prd_slug="${prd_dirs[0]##*/prd-}"
fi

if [[ ! "$prd_slug" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
  echo "Erro: slug inválido: $prd_slug" >&2
  exit 2
fi

tasks_file="$tasks_root/prd-$prd_slug/tasks.md"
if [[ ! -f "$tasks_file" ]]; then
  echo "Erro: arquivo não encontrado: $tasks_file" >&2
  exit 2
fi

pending_count() {
  awk '/^[[:space:]]*-[[:space:]]*\[[[:space:]]\]/ { count++ } END { print count+0 }' "$tasks_file"
}

while :; do
  before="$(pending_count)"
  if ((before == 0)); then
    echo "Todas as tarefas de '$prd_slug' estão concluídas."
    break
  fi

  echo "Tarefas pendentes: $before. Iniciando nova sessão Codex para '$prd_slug'."
  prompt="\$executar-task --prd $prd_slug. Execute somente a próxima tarefa pendente, incluindo todas as suas subtarefas, e pare ao concluí-la. Siga o fluxo da skill até atualizar tasks.md."
  codex exec --skip-git-repo-check --sandbox workspace-write -C "$repo_root" "$prompt"

  after="$(pending_count)"
  if ((after >= before)); then
    echo "Erro: a execução terminou sem reduzir as tarefas pendentes ($before -> $after). Interrompendo para evitar repetição." >&2
    exit 1
  fi
  echo "Tarefa concluída ($before -> $after pendentes). A próxima execução começará em uma sessão nova."
done
