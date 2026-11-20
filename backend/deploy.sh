#!/usr/bin/env bash
#
# Развёртывание PeerFlow на сервере: обновить код из git и пересобрать backend.
#
# Запуск на сервере из каталога клона репозитория:
#   bash backend/deploy.sh              # обновить ветку по умолчанию (master)
#   bash backend/deploy.sh phase-2      # обновить конкретную ветку
#
# Что делает:
#   1. git fetch + переключение на ветку + fast-forward до origin.
#   2. docker compose up -d --build — пересобирает образ и поднимает стек.
#   3. Показывает статус и проверяет живость.
#
# Секреты и данные лежат ВНЕ клона (рядом с ним), поэтому обновление кода их
# не затрагивает:
#   <проект>/.env       — секреты (POSTGRES_PASSWORD, PEERFLOW_JWT_SECRET)
#   <проект>/storage/   — вложения (аватары)
# Настроить пути можно переменными PEERFLOW_ENV_FILE и PEERFLOW_STORAGE_DIR.

set -euo pipefail

BRANCH="${1:-master}"

# Каталог скрипта — это <клон>/backend; корень клона — на уровень выше.
BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$BACKEND_DIR/.." && pwd)"

# Каталог развёртывания (секреты, данные) по умолчанию — родитель клона.
PROJECT_DIR="${PEERFLOW_PROJECT_DIR:-$(cd "$REPO_DIR/.." && pwd)}"
ENV_FILE="${PEERFLOW_ENV_FILE:-$PROJECT_DIR/.env}"
COMPOSE_FILE="$BACKEND_DIR/compose.prod.yaml"

say() { printf '\n=== %s ===\n' "$1"; }

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Не найден файл секретов: $ENV_FILE" >&2
  echo "Скопируйте backend/.env.example в него и заполните значения." >&2
  exit 1
fi

say "Обновление кода (ветка $BRANCH)"
git -C "$REPO_DIR" fetch --prune origin
git -C "$REPO_DIR" checkout "$BRANCH"
git -C "$REPO_DIR" pull --ff-only origin "$BRANCH"
git -C "$REPO_DIR" --no-pager log -1 --format='Сейчас на коммите: %h %s'

say "Сборка и запуск стека"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build

say "Статус"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps

say "Проверка живости"
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8082/actuator/health >/dev/null 2>&1; then
    echo "Backend поднялся: $(curl -fsS http://127.0.0.1:8082/actuator/health)"
    exit 0
  fi
  sleep 2
done

echo "Backend не ответил за минуту — проверьте логи:" >&2
echo "  docker compose --env-file $ENV_FILE -f $COMPOSE_FILE logs --tail=50 backend" >&2
exit 1
