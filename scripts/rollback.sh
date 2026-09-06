#!/usr/bin/env bash
set -euo pipefail

ROOT="${AI_ERP_ROOT:-/home/deploy/ai-erp}"
PROJECT_DIR="${AI_ERP_PROJECT_DIR:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)}"
TARGET_RELEASE="${1:-}"
ENV_FILE="$ROOT/shared/.env"
STATE_DIR="$ROOT/shared/caddy"
ACTIVE_FILE="$ROOT/shared/active-color"
ACTIVE_RELEASE_FILE="$ROOT/shared/active-release"
COMPOSE=(docker compose --project-name ai-erp-phase1 --env-file "$ENV_FILE" -f "$PROJECT_DIR/infra/compose.prod.yml")

fail() { printf 'rollback: %s\n' "$*" >&2; exit 1; }
[[ "$TARGET_RELEASE" =~ ^[a-f0-9]{40}$ ]] || fail 'releaseId must be an exact lowercase 40-character commit SHA'
MANIFEST="$ROOT/releases/$TARGET_RELEASE/manifest.json"
[[ -f "$MANIFEST" ]] || fail 'preserved release manifest is missing'
image="$(sed -n 's/.*"image":"\([^"]*\)".*/\1/p' "$MANIFEST" | head -n 1)"
color="$(sed -n 's/.*"color":"\(blue\|green\)".*/\1/p' "$MANIFEST" | head -n 1)"
[[ -n "$image" && ( "$color" == blue || "$color" == green ) ]] || fail 'release manifest is invalid'
previous_color="$(tr -d '[:space:]' < "$ACTIVE_FILE" 2>/dev/null || true)"
mkdir -p "$STATE_DIR"
if [[ ! -f "$STATE_DIR/active-upstream.caddy" ]]; then
  printf 'respond "service initializing" 503\n' >"$STATE_DIR/active-upstream.caddy"
  chmod 600 "$STATE_DIR/active-upstream.caddy"
fi
export APP_IMAGE="$image" CADDY_STATE_DIR="$STATE_DIR" RELEASE_SOURCE_DIR="$PROJECT_DIR"
"${COMPOSE[@]}" up -d postgres redis caddy
"${COMPOSE[@]}" up -d --no-deps "app-$color"
"$(dirname -- "$0")/smoke.sh" internal "$color"

candidate="$STATE_DIR/active-upstream.caddy.candidate"
{
  printf 'header X-AI-ERP-Release "%s"\n' "$TARGET_RELEASE"
  printf 'reverse_proxy app-%s:8080\n' "$color"
} >"$candidate"
chmod 600 "$candidate"
"${COMPOSE[@]}" run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
mv -f "$candidate" "$STATE_DIR/active-upstream.caddy"
"${COMPOSE[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
printf '%s\n' "$color" >"$ACTIVE_FILE"
chmod 600 "$ACTIVE_FILE"
printf '%s\n' "$TARGET_RELEASE" >"$ACTIVE_RELEASE_FILE"
chmod 600 "$ACTIVE_RELEASE_FILE"
if [[ "$previous_color" == blue || "$previous_color" == green ]] && [[ "$previous_color" != "$color" ]]; then
  "${COMPOSE[@]}" stop "app-$previous_color"
fi
printf 'rollback: ok release=%s\n' "$TARGET_RELEASE"
