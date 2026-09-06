#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=rollback
source "$(dirname -- "$0")/lib-deploy.sh"
TARGET_RELEASE="${1:-}"
exec 9>"$LOCK_FILE"
if [[ "${AI_ERP_LOCK_HELD:-}" != 1 ]]; then flock -n 9 || fail 'another deployment holds the lock'; fi
load_env; validate_environment; load_state
if [[ -z "$TARGET_RELEASE" ]]; then
  [[ -n "$STATE_RELEASE" ]] || fail 'no active release supplies a rollback target'
  TARGET_RELEASE="$(sed -n 's/.*"previousRelease":"\([a-f0-9]\{40\}\)".*/\1/p' "$ROOT/releases/$STATE_RELEASE/manifest.json" | head -n1)"
fi
valid_release "$TARGET_RELEASE" || fail 'rollback target must be an exact lowercase 40-character commit SHA'
MANIFEST="$ROOT/releases/$TARGET_RELEASE/manifest.json"; [[ -f "$MANIFEST" ]] || fail 'preserved release manifest is missing'
image_id="$(sed -n 's/.*"imageId":"\(sha256:[a-f0-9]\{64\}\)".*/\1/p' "$MANIFEST" | head -n1)"
revision="$(sed -n 's/.*"revision":"\([a-f0-9]\{40\}\)".*/\1/p' "$MANIFEST" | head -n1)"
checksum="$(sed -n 's/.*"imageChecksum":"\([a-f0-9]\{64\}\)".*/\1/p' "$MANIFEST" | head -n1)"
[[ "$revision" == "$TARGET_RELEASE" && "$image_id" =~ ^sha256:[a-f0-9]{64}$ && "$checksum" == "$(printf %s "$image_id" | sha256sum | awk '{print $1}')" ]] || fail 'rollback manifest is tampered'
image_identity "$image_id" "$TARGET_RELEASE"
if [[ "$STATE_COLOR" == blue ]]; then TARGET_COLOR=green; else TARGET_COLOR=blue; fi
export APP_IMAGE="$image_id" CADDY_STATE_DIR="$STATE_DIR" RELEASE_SOURCE_DIR="$PROJECT_DIR"
"${COMPOSE[@]}" up -d postgres redis caddy
"${COMPOSE[@]}" up -d --no-deps "app-$TARGET_COLOR"
wait_healthy "$TARGET_COLOR" || fail 'rollback candidate did not become healthy'
"$(dirname -- "$0")/smoke.sh" internal "$TARGET_COLOR"
transactional_promote "$TARGET_RELEASE" "$TARGET_COLOR" "$IMAGE_ID" "$IMAGE_REVISION"
if [[ "${SKIP_PUBLIC_SMOKE:-0}" != 1 ]]; then "$(dirname -- "$0")/smoke.sh" public "$TARGET_RELEASE"; fi
[[ -n "$STATE_COLOR" ]] && "${COMPOSE[@]}" stop "app-$STATE_COLOR"
printf 'rollback: ok release=%s color=%s\n' "$TARGET_RELEASE" "$TARGET_COLOR"
