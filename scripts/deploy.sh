#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=deploy
source "$(dirname -- "$0")/lib-deploy.sh"
RELEASE_ID="${1:-}"; STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; SWITCHED=0
valid_release "$RELEASE_ID" || fail 'releaseId must be an exact lowercase 40-character commit SHA'
mkdir -p "$ROOT/releases/$RELEASE_ID" "$STATE_DIR"; chmod 700 "$ROOT/releases/$RELEASE_ID" "$STATE_DIR"
if [[ ! -f "$STATE_DIR/active-upstream.caddy" ]]; then printf 'respond "service initializing" 503\n' >"$STATE_DIR/active-upstream.caddy"; chmod 600 "$STATE_DIR/active-upstream.caddy"; fi
exec 9>"$LOCK_FILE"; flock -n 9 || fail 'another deployment holds the lock'
AI_ERP_LOCK_HELD=1 "$(dirname -- "$0")/preflight.sh" "$RELEASE_ID"
load_env; load_state
PREVIOUS_RELEASE="$STATE_RELEASE"; PREVIOUS_COLOR="$STATE_COLOR"
if [[ "$STATE_COLOR" == blue ]]; then INACTIVE_COLOR=green; else INACTIVE_COLOR=blue; fi
rollback_failure() {
  [[ "$SWITCHED" == 1 ]] || return 0
  if [[ -n "$PREVIOUS_RELEASE" ]]; then AI_ERP_LOCK_HELD=1 SKIP_PUBLIC_SMOKE=1 "$(dirname -- "$0")/rollback.sh" "$PREVIOUS_RELEASE" || true
  else
    printf 'respond "deployment failed" 503\n' >"$STATE_DIR/active-upstream.caddy"
    "${COMPOSE[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null || true
    rm -f "$STATE_FILE"
  fi
}
trap rollback_failure ERR
export APP_IMAGE="ai-erp:$RELEASE_ID" CADDY_STATE_DIR="$STATE_DIR" RELEASE_SOURCE_DIR="$PROJECT_DIR"
docker build --pull --label "org.opencontainers.image.revision=$RELEASE_ID" --tag "$APP_IMAGE" "$PROJECT_DIR"
image_identity "$APP_IMAGE" "$RELEASE_ID"; BUILT_IMAGE_ID="$IMAGE_ID"; BUILT_REVISION="$IMAGE_REVISION"
"${COMPOSE[@]}" up -d postgres redis caddy
if "${COMPOSE[@]}" exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "select to_regclass('platform.flyway_schema_history') is not null" | grep -qx t; then "$(dirname -- "$0")/backup.sh" "$RELEASE_ID"; fi
"${COMPOSE[@]}" --profile migration run --rm migrate >/dev/null
MIGRATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
"${COMPOSE[@]}" up -d --no-deps "app-$INACTIVE_COLOR"
wait_healthy "$INACTIVE_COLOR" || fail 'inactive application slot did not become healthy'
candidate_id="$("${COMPOSE[@]}" ps -q "app-$INACTIVE_COLOR")"; [[ "$(docker inspect -f '{{.Image}}' "$candidate_id")" == "$BUILT_IMAGE_ID" ]] || fail 'candidate container does not use built image identity'
image_identity "$BUILT_IMAGE_ID" "$RELEASE_ID"
"$(dirname -- "$0")/smoke.sh" internal "$INACTIVE_COLOR"
transactional_promote "$RELEASE_ID" "$INACTIVE_COLOR" "$BUILT_IMAGE_ID" "$BUILT_REVISION"
SWITCHED=1; SWITCHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
"$(dirname -- "$0")/smoke.sh" public "$RELEASE_ID"; sleep "${OBSERVATION_SECONDS:-20}"; "$(dirname -- "$0")/smoke.sh" public "$RELEASE_ID"
[[ -n "$PREVIOUS_COLOR" ]] && "${COMPOSE[@]}" stop "app-$PREVIOUS_COLOR"
manifest="$ROOT/releases/$RELEASE_ID/manifest.json"; tmp="$manifest.tmp"
printf '{"releaseId":"%s","revision":"%s","imageId":"%s","imageChecksum":"%s","color":"%s","previousRelease":"%s","previousColor":"%s","startedAt":"%s","migratedAt":"%s","switchedAt":"%s","completedAt":"%s","composeChecksum":"%s","caddyChecksum":"%s"}\n' "$RELEASE_ID" "$BUILT_REVISION" "$BUILT_IMAGE_ID" "$(printf %s "$BUILT_IMAGE_ID" | sha256sum | awk '{print $1}')" "$INACTIVE_COLOR" "$PREVIOUS_RELEASE" "$PREVIOUS_COLOR" "$STARTED_AT" "$MIGRATED_AT" "$SWITCHED_AT" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(sha256 "$COMPOSE_FILE")" "$(sha256 "$PROJECT_DIR/infra/Caddyfile")" >"$tmp"
chmod 600 "$tmp"; mv -f "$tmp" "$manifest"
printf 'deploy: ok release=%s color=%s\n' "$RELEASE_ID" "$INACTIVE_COLOR"
