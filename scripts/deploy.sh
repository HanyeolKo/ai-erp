#!/usr/bin/env bash
set -euo pipefail

ROOT="${AI_ERP_ROOT:-/home/deploy/ai-erp}"
PROJECT_DIR="${AI_ERP_PROJECT_DIR:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)}"
RELEASE_ID="${1:-}"
ENV_FILE="$ROOT/shared/.env"
STATE_DIR="$ROOT/shared/caddy"
ACTIVE_FILE="$ROOT/shared/active-color"
ACTIVE_RELEASE_FILE="$ROOT/shared/active-release"
LOCK_FILE="$ROOT/shared/deploy.lock"
RELEASE_DIR="$ROOT/releases/$RELEASE_ID"
COMPOSE=(docker compose --project-name ai-erp-phase1 --env-file "$ENV_FILE" -f "$PROJECT_DIR/infra/compose.prod.yml")
SWITCHED=0
PREVIOUS_RELEASE=""

fail() { printf 'deploy: %s\n' "$*" >&2; exit 1; }
valid_release() { [[ "$1" =~ ^[a-f0-9]{40}$ ]]; }
wait_healthy() {
  local color="$1" attempt container status
  for attempt in $(seq 1 "${HEALTH_ATTEMPTS:-36}"); do
    container="$("${COMPOSE[@]}" ps -q "app-$color")"
    if [[ -n "$container" ]]; then
      status="$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || true)"
      [[ "$status" == healthy ]] && return 0
    fi
    sleep "${HEALTH_INTERVAL_SECONDS:-5}"
  done
  return 1
}
write_upstream() {
  local color="$1" release="$2" candidate="$STATE_DIR/active-upstream.caddy.candidate"
  {
    printf 'header X-AI-ERP-Release "%s"\n' "$release"
    printf 'reverse_proxy app-%s:8080\n' "$color"
  } >"$candidate"
  chmod 600 "$candidate"
  "${COMPOSE[@]}" run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
  mv -f "$candidate" "$STATE_DIR/active-upstream.caddy"
  "${COMPOSE[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
}
rollback_after_switch() {
  if [[ "$SWITCHED" == 1 ]]; then
    if [[ -n "$PREVIOUS_RELEASE" ]]; then
      "$(dirname -- "$0")/rollback.sh" "$PREVIOUS_RELEASE" || true
    else
      printf 'respond "deployment failed" 503\n' >"$STATE_DIR/active-upstream.caddy"
      "${COMPOSE[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null || true
      : >"$ACTIVE_FILE"
      : >"$ACTIVE_RELEASE_FILE"
    fi
  fi
}
trap rollback_after_switch ERR

valid_release "$RELEASE_ID" || fail 'releaseId must be an exact lowercase 40-character commit SHA'
mkdir -p "$RELEASE_DIR" "$STATE_DIR"
chmod 700 "$RELEASE_DIR" "$STATE_DIR"
if [[ ! -f "$STATE_DIR/active-upstream.caddy" ]]; then
  printf 'respond "service initializing" 503\n' >"$STATE_DIR/active-upstream.caddy"
  chmod 600 "$STATE_DIR/active-upstream.caddy"
fi
exec 9>"$LOCK_FILE"
flock -n 9 || fail 'another deployment holds the lock'
AI_ERP_LOCK_HELD=1 "$(dirname -- "$0")/preflight.sh" "$RELEASE_ID"

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
export APP_IMAGE="ai-erp:$RELEASE_ID" CADDY_STATE_DIR="$STATE_DIR" RELEASE_SOURCE_DIR="$PROJECT_DIR"
docker build --pull --tag "$APP_IMAGE" "$PROJECT_DIR"
"${COMPOSE[@]}" up -d postgres redis caddy
if "${COMPOSE[@]}" ps -q postgres | grep -q .; then
  "$(dirname -- "$0")/backup.sh" "$RELEASE_ID"
fi
"${COMPOSE[@]}" --profile migration run --rm migrate >/dev/null

ACTIVE_COLOR=""
if [[ -f "$ACTIVE_FILE" ]]; then
  ACTIVE_COLOR="$(tr -d '[:space:]' < "$ACTIVE_FILE")"
fi
RECORDED_ACTIVE_RELEASE=""
if [[ -f "$ACTIVE_RELEASE_FILE" ]]; then
  RECORDED_ACTIVE_RELEASE="$(tr -d '[:space:]' < "$ACTIVE_RELEASE_FILE")"
fi
if ! valid_release "$RECORDED_ACTIVE_RELEASE" || [[ ! -f "$ROOT/releases/$RECORDED_ACTIVE_RELEASE/manifest.json" ]]; then
  ACTIVE_COLOR=""
fi
if [[ "$ACTIVE_COLOR" == blue ]]; then INACTIVE_COLOR=green; else INACTIVE_COLOR=blue; fi
if [[ "$ACTIVE_COLOR" == blue || "$ACTIVE_COLOR" == green ]]; then
  PREVIOUS_RELEASE="$RECORDED_ACTIVE_RELEASE"
  valid_release "$PREVIOUS_RELEASE" || fail 'active color has no valid preserved release'
fi

"${COMPOSE[@]}" up -d --no-deps "app-$INACTIVE_COLOR"
wait_healthy "$INACTIVE_COLOR" || fail 'inactive application slot did not become healthy'
"$(dirname -- "$0")/smoke.sh" internal "$INACTIVE_COLOR"
write_upstream "$INACTIVE_COLOR" "$RELEASE_ID"
printf '%s\n' "$INACTIVE_COLOR" >"$ACTIVE_FILE"
chmod 600 "$ACTIVE_FILE"
printf '%s\n' "$RELEASE_ID" >"$ACTIVE_RELEASE_FILE"
chmod 600 "$ACTIVE_RELEASE_FILE"
SWITCHED=1
"$(dirname -- "$0")/smoke.sh" public "$RELEASE_ID"
sleep "${OBSERVATION_SECONDS:-20}"
"$(dirname -- "$0")/smoke.sh" public "$RELEASE_ID"
if [[ "$ACTIVE_COLOR" == blue || "$ACTIVE_COLOR" == green ]]; then
  "${COMPOSE[@]}" stop "app-$ACTIVE_COLOR"
fi

image_id="$(docker image inspect --format '{{.Id}}' "$APP_IMAGE")"
compose_checksum="$(sha256sum "$PROJECT_DIR/infra/compose.prod.yml" | awk '{print $1}')"
image_checksum="$(printf '%s' "$image_id" | sha256sum | awk '{print $1}')"
manifest_tmp="$RELEASE_DIR/manifest.json.tmp"
printf '{"releaseId":"%s","commit":"%s","image":"%s","imageId":"%s","imageChecksum":"%s","composeChecksum":"%s","color":"%s","deployedAt":"%s"}\n' \
  "$RELEASE_ID" "$RELEASE_ID" "$APP_IMAGE" "$image_id" "$image_checksum" "$compose_checksum" "$INACTIVE_COLOR" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >"$manifest_tmp"
chmod 600 "$manifest_tmp"
mv -f "$manifest_tmp" "$RELEASE_DIR/manifest.json"
printf 'deploy: ok release=%s color=%s\n' "$RELEASE_ID" "$INACTIVE_COLOR"
