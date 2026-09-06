#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=preflight
source "$(dirname -- "$0")/lib-deploy.sh"
RELEASE_ID="${1:-}"
valid_release "$RELEASE_ID" || fail 'releaseId must be an exact lowercase 40-character commit SHA'
if [[ "${AI_ERP_TEST_MODE:-0}" != 1 ]]; then
  [[ "$ROOT" == /home/deploy/ai-erp && "$PROJECT_DIR" == /home/deploy/actions-runner-ai-erp/_work/ai-erp/ai-erp ]] || fail 'unexpected production root or project directory'
fi
[[ -d "$ROOT/shared" && -d "$ROOT/releases" && -d "$STATE_DIR" ]] || fail 'host directories are incomplete'
[[ -f "$ENV_FILE" && "$(stat -c '%a' "$ENV_FILE")" == 600 ]] || fail 'shared environment file must exist with mode 600'
[[ -f "$COMPOSE_FILE" ]] || fail 'production Compose file is missing'
find "$PROJECT_DIR/backend/src/main/resources/db/migration" -maxdepth 1 -type f -name 'V*__*.sql' | grep -q . || fail 'Flyway migration files are missing'
load_env; validate_environment; load_state
command -v docker >/dev/null && docker compose version >/dev/null || fail 'Docker or Docker Compose is unavailable'
git -C "$PROJECT_DIR" rev-parse --verify HEAD^{commit} >/dev/null || fail 'repository HEAD is not a commit'
[[ "$(git -C "$PROJECT_DIR" rev-parse HEAD)" == "$RELEASE_ID" ]] || fail 'releaseId does not match repository HEAD'
[[ -z "$(git -C "$PROJECT_DIR" status --porcelain --untracked-files=no)" ]] || fail 'tracked worktree is not clean'
[[ "$(df -Pk "$ROOT" | awk 'NR==2 {print $4}')" -ge 20971520 ]] || fail 'free disk is below 20 GiB'
[[ "${AI_ERP_AVAILABLE_MEM_KIB:-$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)}" -ge 2621440 ]] || fail 'available memory is below 2.5 GiB'
"${COMPOSE[@]}" config --quiet >/dev/null
for port in 80 443; do
  if ss -ltn "sport = :$port" | grep -q LISTEN; then
    docker ps -q --filter "publish=$port" | while IFS= read -r id; do
      [[ -z "$id" ]] && continue
      [[ "$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$id")" == ai-erp-phase1 ]] || fail "port $port is owned by a non-project container"
    done
  fi
done
if [[ "${AI_ERP_LOCK_HELD:-}" != 1 ]]; then exec 9>"$LOCK_FILE"; flock -n 9 || fail 'another deployment holds the lock'; fi
printf 'preflight: ok release=%s\n' "$RELEASE_ID"
