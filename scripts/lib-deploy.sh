#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="${AI_ERP_ROOT:-/home/deploy/ai-erp}"
PROJECT_DIR="${AI_ERP_PROJECT_DIR:-$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}"
ENV_FILE="$ROOT/shared/.env"
STATE_DIR="$ROOT/shared/caddy"
STATE_FILE="$ROOT/shared/active-state.json"
LOCK_FILE="$ROOT/shared/deploy.lock"
COMPOSE_FILE="$PROJECT_DIR/infra/compose.prod.yml"
COMPOSE=(docker compose --project-name ai-erp-phase1 --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

fail() { printf '%s: %s\n' "${SCRIPT_NAME:-deploy}" "$*" >&2; exit 1; }
valid_release() { [[ "$1" =~ ^[a-f0-9]{40}$ ]]; }
sha256() { sha256sum "$1" | awk '{print $1}'; }
load_env() {
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}
validate_environment() {
  local required
  for required in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD DB_USERNAME DB_PASSWORD DB_URL REDIS_URL REDIS_PASSWORD SESSION_SECRET APP_OIDC_ENABLED SITE_ADDRESS; do
    [[ -n "${!required:-}" ]] || fail "required environment key is missing: $required"
  done
  [[ "$DB_USERNAME" == "$POSTGRES_USER" && "$DB_PASSWORD" == "$POSTGRES_PASSWORD" ]] || fail 'database credentials must target the project PostgreSQL service'
  [[ "$DB_URL" == "jdbc:postgresql://postgres:5432/$POSTGRES_DB" ]] || fail 'DB_URL must target postgres:5432 and POSTGRES_DB exactly'
  [[ "$REDIS_PASSWORD" =~ ^[A-Za-z0-9._~-]{24,128}$ ]] || fail 'REDIS_PASSWORD must be URL-safe and 24-128 characters'
  [[ "$REDIS_URL" == "redis://:$REDIS_PASSWORD@redis:6379/0" ]] || fail 'REDIS_URL must target internal redis:6379 with REDIS_PASSWORD'
  [[ "$APP_OIDC_ENABLED" == true || "$APP_OIDC_ENABLED" == false ]] || fail 'APP_OIDC_ENABLED must be true or false'
  if [[ "$APP_OIDC_ENABLED" == true ]]; then
    [[ -n "${GOOGLE_CLIENT_ID:-}" && -n "${GOOGLE_CLIENT_SECRET:-}" ]] || fail 'Google credentials are required when OIDC is enabled'
  fi
}
load_state() {
  STATE_COLOR="" STATE_RELEASE="" STATE_IMAGE_ID=""
  [[ -e "$STATE_FILE" ]] || return 0
  [[ -f "$STATE_FILE" ]] || fail 'active state must be a regular file'
  STATE_COLOR="$(sed -n 's/.*"color":"\(blue\|green\)".*/\1/p' "$STATE_FILE" | head -n 1)"
  STATE_RELEASE="$(sed -n 's/.*"releaseId":"\([a-f0-9]\{40\}\)".*/\1/p' "$STATE_FILE" | head -n 1)"
  STATE_IMAGE_ID="$(sed -n 's/.*"imageId":"\(sha256:[a-f0-9]\{64\}\)".*/\1/p' "$STATE_FILE" | head -n 1)"
  [[ ( "$STATE_COLOR" == blue || "$STATE_COLOR" == green ) && $(valid_release "$STATE_RELEASE"; echo $?) == 0 && "$STATE_IMAGE_ID" =~ ^sha256:[a-f0-9]{64}$ ]] || fail 'active state is invalid'
}
write_state_candidate() {
  local release="$1" color="$2" image_id="$3" revision="$4" candidate="$STATE_DIR/active-state.json.candidate"
  printf '{"releaseId":"%s","color":"%s","imageId":"%s","revision":"%s","updatedAt":"%s"}\n' "$release" "$color" "$image_id" "$revision" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >"$candidate"
  chmod 600 "$candidate"
}
write_upstream_candidate() {
  local release="$1" color="$2" upstream="$STATE_DIR/candidate-upstream.caddy" full="$STATE_DIR/candidate.Caddyfile"
  printf 'header X-AI-ERP-Release "%s"\nreverse_proxy app-%s:8080\n' "$release" "$color" >"$upstream"
  sed 's#active-upstream\.caddy#candidate-upstream.caddy#' "$PROJECT_DIR/infra/Caddyfile" >"$full"
  chmod 600 "$upstream" "$full"
}
validate_candidate_caddy() {
  "${COMPOSE[@]}" run --rm --no-deps caddy caddy validate --config /etc/caddy/state/candidate.Caddyfile --adapter caddyfile >/dev/null
}
reload_caddy() { "${COMPOSE[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null; }
restore_caddy_state() {
  local old_upstream="$1" old_state="$2"
  if [[ -f "$old_upstream" ]]; then mv -f "$old_upstream" "$STATE_DIR/active-upstream.caddy"; else rm -f "$STATE_DIR/active-upstream.caddy"; fi
  if [[ -f "$old_state" ]]; then mv -f "$old_state" "$STATE_FILE"; else rm -f "$STATE_FILE"; fi
  reload_caddy || true
}
transactional_promote() {
  local release="$1" color="$2" image_id="$3" revision="$4" old_upstream="$STATE_DIR/active-upstream.caddy.previous" old_state="$STATE_DIR/active-state.json.previous"
  [[ -f "$STATE_DIR/active-upstream.caddy" ]] && cp "$STATE_DIR/active-upstream.caddy" "$old_upstream" || : >"$old_upstream"
  [[ -f "$STATE_FILE" ]] && cp "$STATE_FILE" "$old_state" || : >"$old_state"
  write_upstream_candidate "$release" "$color"
  write_state_candidate "$release" "$color" "$image_id" "$revision"
  validate_candidate_caddy || { restore_caddy_state "$old_upstream" "$old_state"; return 1; }
  mv -f "$STATE_DIR/candidate-upstream.caddy" "$STATE_DIR/active-upstream.caddy"
  if ! reload_caddy; then restore_caddy_state "$old_upstream" "$old_state"; return 1; fi
  if ! mv -f "$STATE_DIR/active-state.json.candidate" "$STATE_FILE"; then restore_caddy_state "$old_upstream" "$old_state"; return 1; fi
  rm -f "$old_upstream" "$old_state" "$STATE_DIR/candidate.Caddyfile"
}
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
wait_service_healthy() {
  local service="$1" attempt container status
  for attempt in $(seq 1 "${HEALTH_ATTEMPTS:-36}"); do
    container="$("${COMPOSE[@]}" ps -q "$service")"
    if [[ -n "$container" ]]; then
      status="$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || true)"
      [[ "$status" == healthy ]] && return 0
    fi
    sleep "${HEALTH_INTERVAL_SECONDS:-5}"
  done
  return 1
}
image_identity() {
  local image="$1" details
  details="$(docker image inspect --format '{{.Id}} {{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")"
  IMAGE_ID="${details%% *}" IMAGE_REVISION="${details#* }"
  [[ "$IMAGE_ID" =~ ^sha256:[a-f0-9]{64}$ && "$IMAGE_REVISION" == "$2" ]] || fail 'image identity or revision label is invalid'
}
