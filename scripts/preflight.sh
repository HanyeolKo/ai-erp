#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=preflight
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
printf 'preflight: ok release=%s\n' "$RELEASE_ID"
