#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=rollback
source "$(dirname -- "$0")/lib-deploy.sh"
[[ $# -le 1 ]] || fail 'rollback accepts at most one releaseId'
RELEASE_ID="${1:-}"
if [[ $# == 1 ]]; then valid_release "$RELEASE_ID" || fail 'rollback target must be an exact lowercase 40-character commit SHA'; fi
validate_base
acquire_lock
verify_no_transaction
load_env
export APP_IMAGE=ai-erp:preflight
verify_host
load_state recovery
[[ -n "$STATE_RELEASE" ]] || fail 'no active deployment exists'
RELEASE_ID="${RELEASE_ID:-$STATE_PREVIOUS_RELEASE}"
valid_release "$RELEASE_ID" || fail 'no previous release exists'
if [[ "$RELEASE_ID" == "$STATE_RELEASE" ]]; then
  "$(dirname -- "$0")/smoke.sh" public "$RELEASE_ID"
  printf 'rollback: verified active release=%s (no-op)\n' "$RELEASE_ID"
  exit 0
fi
validate_manifest "$RELEASE_ID"
CANDIDATE_IMAGE="$MANIFEST_IMAGE"
export APP_IMAGE="$CANDIDATE_IMAGE"
prepare_release
install_error_trap
if [[ "$STATE_COLOR" == blue ]]; then CANDIDATE_COLOR=green; else CANDIDATE_COLOR=blue; fi
for service in postgres redis caddy; do wait_service_healthy "$service"; done
CANDIDATE_STARTED=1
"${COMPOSE[@]}" up -d --no-deps "app-$CANDIDATE_COLOR" >/dev/null 2>&1 || fail 'rollback candidate start failed'
wait_service_healthy "app-$CANDIDATE_COLOR"
verify_container "app-$CANDIDATE_COLOR" "$CANDIDATE_IMAGE"
"$(dirname -- "$0")/smoke.sh" internal "$CANDIDATE_COLOR"
transactional_promote 0
printf 'rollback: ok release=%s color=%s\n' "$RELEASE_ID" "$CANDIDATE_COLOR"
