#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=deploy
source "$(dirname -- "$0")/lib-deploy.sh"
RELEASE_ID="${1:-}"
[[ $# == 1 ]] && valid_release "$RELEASE_ID" || fail 'releaseId must be an exact lowercase 40-character commit SHA'
validate_base
acquire_lock
verify_no_transaction
load_env
export APP_IMAGE="ai-erp:$RELEASE_ID"
verify_source
verify_migrations
verify_host
load_state
if [[ "$STATE_RELEASE" == "$RELEASE_ID" ]]; then
  "$(dirname -- "$0")/smoke.sh" public "$RELEASE_ID"
  printf 'deploy: verified active release=%s (no-op)\n' "$RELEASE_ID"
  exit 0
fi
[[ ! -e "$ROOT/releases/$RELEASE_ID/manifest.json" && ! -L "$ROOT/releases/$RELEASE_ID/manifest.json" ]] || fail 'completed non-active release exists; use rollback'
STARTED_AT="$(now)"
prepare_release
install_error_trap
initialize_upstream
MIGRATION_CHECKSUM="$(migration_checksum)"
event build
docker build --pull --label "org.opencontainers.image.revision=$RELEASE_ID" --tag "$APP_IMAGE" "$PROJECT_DIR" >/dev/null 2>&1 || fail 'immutable application build failed'
image_identity "$APP_IMAGE" "$RELEASE_ID"
CANDIDATE_IMAGE="$IMAGE_ID"
"${COMPOSE[@]}" up -d postgres redis caddy >/dev/null 2>&1 || fail 'infrastructure start failed'
for service in postgres redis caddy; do wait_service_healthy "$service"; done
event infrastructure-ready
"$(dirname -- "$0")/backup.sh" "$RELEASE_ID"
# Flyway reads host-mounted SQL: revalidate both provenance and the measured SQL
# set immediately before its only invocation.
verify_source
[[ "$MIGRATION_CHECKSUM" == "$(migration_checksum)" ]] || fail 'migration inputs changed after build'
"${COMPOSE[@]}" --profile migration run --rm migrate >/dev/null 2>&1 || fail 'external Flyway migration failed'
MIGRATED_AT="$(now)"; event migrated
if [[ "$STATE_COLOR" == blue ]]; then CANDIDATE_COLOR=green; else CANDIDATE_COLOR=blue; fi
CANDIDATE_STARTED=1
"${COMPOSE[@]}" up -d --no-deps "app-$CANDIDATE_COLOR" >/dev/null 2>&1 || fail 'candidate start failed'
wait_service_healthy "app-$CANDIDATE_COLOR"
verify_container "app-$CANDIDATE_COLOR" "$CANDIDATE_IMAGE"
image_identity "$APP_IMAGE" "$RELEASE_ID"
[[ "$IMAGE_ID" == "$CANDIDATE_IMAGE" ]] || fail 'candidate tag changed during deployment'
"$(dirname -- "$0")/smoke.sh" internal "$CANDIDATE_COLOR"
transactional_promote 1
printf 'deploy: ok release=%s color=%s\n' "$RELEASE_ID" "$CANDIDATE_COLOR"
