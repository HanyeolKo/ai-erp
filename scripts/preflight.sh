#!/usr/bin/env bash
set -euo pipefail

ROOT="${AI_ERP_ROOT:-/home/deploy/ai-erp}"
PROJECT_DIR="${AI_ERP_PROJECT_DIR:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)}"
RELEASE_ID="${1:-}"
ENV_FILE="$ROOT/shared/.env"
LOCK_FILE="$ROOT/shared/deploy.lock"
COMPOSE_FILE="$PROJECT_DIR/infra/compose.prod.yml"

fail() { printf 'preflight: %s\n' "$*" >&2; exit 1; }
valid_release() { [[ "$1" =~ ^[a-f0-9]{40}$ ]]; }
valid_release "$RELEASE_ID" || fail 'releaseId must be an exact lowercase 40-character commit SHA'
[[ "$ROOT" == "/home/deploy/ai-erp" || -n "${AI_ERP_ROOT:-}" ]] || fail 'unexpected host root'
[[ -d "$ROOT/shared" && -d "$ROOT/releases" ]] || fail 'host directories are incomplete'
[[ -f "$ENV_FILE" ]] || fail 'shared environment file is missing'
[[ "$(stat -c '%a' "$ENV_FILE")" == "600" ]] || fail 'shared environment file must have mode 600'
[[ -f "$COMPOSE_FILE" ]] || fail 'production Compose file is missing'
[[ -d "$PROJECT_DIR/backend/src/main/resources/db/migration" ]] || fail 'Flyway migrations are missing'

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
for required in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD DB_USERNAME DB_PASSWORD DB_URL REDIS_URL SESSION_SECRET OIDC_ENABLED SITE_ADDRESS; do
  [[ -n "${!required:-}" ]] || fail "required environment key is missing: $required"
done

command -v docker >/dev/null || fail 'Docker is unavailable'
docker compose version >/dev/null || fail 'Docker Compose is unavailable'
[[ "$(df -Pk "$ROOT" | awk 'NR==2 {print $4}')" -ge 20971520 ]] || fail 'free disk is below 20 GiB'
available_memory_kib="${AI_ERP_AVAILABLE_MEM_KIB:-$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)}"
[[ "$available_memory_kib" -ge 2621440 ]] || fail 'available memory is below 2.5 GiB'

ACTIVE_FILE="$ROOT/shared/active-color"
ACTIVE_RELEASE_FILE="$ROOT/shared/active-release"
if [[ -f "$ACTIVE_FILE" ]]; then
  ACTIVE_COLOR="$(tr -d '[:space:]' < "$ACTIVE_FILE")"
  [[ "$ACTIVE_COLOR" == blue || "$ACTIVE_COLOR" == green ]] || fail 'active color is invalid'
fi
if [[ -f "$ACTIVE_RELEASE_FILE" ]]; then
  recorded_release="$(tr -d '[:space:]' < "$ACTIVE_RELEASE_FILE")"
  valid_release "$recorded_release" || fail 'active release is invalid'
fi

for port in 80 443; do
  while IFS= read -r container_id; do
    [[ -z "$container_id" ]] && continue
    project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$container_id" 2>/dev/null || true)"
    [[ "$project" == ai-erp-phase1 ]] || fail "port $port is owned by a non-project container"
  done < <(docker ps -q --filter "publish=$port")
done

if [[ "${AI_ERP_LOCK_HELD:-}" != 1 ]]; then
  exec 9>"$LOCK_FILE"
  flock -n 9 || fail 'another deployment holds the lock'
fi

printf 'preflight: ok release=%s\n' "$RELEASE_ID"
