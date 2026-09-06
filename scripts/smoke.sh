#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=smoke
source "$(dirname -- "$0")/lib-deploy.sh"
MODE="${1:-}" VALUE="${2:-}"
[[ $# == 2 ]] || fail 'smoke requires mode and color/releaseId'
case "$MODE" in internal) [[ "$VALUE" == blue || "$VALUE" == green ]] || fail 'invalid smoke color';; public) valid_release "$VALUE" || fail 'invalid smoke releaseId';; *) fail 'invalid smoke mode';; esac
validate_base
load_env
export APP_IMAGE="${APP_IMAGE:-ai-erp:smoke}"
paths=(/actuator/health/readiness / /api/v1/system/configuration /api/v1/system/info /assets/api-docs/index.html)
curl_args=(--silent --show-error --fail --connect-timeout 3 --max-time 10)
for path in "${paths[@]}"; do
  if [[ "$MODE" == internal ]]; then
    body="$("${COMPOSE[@]}" exec -T "app-$VALUE" curl "${curl_args[@]}" "http://127.0.0.1:8080$path" 2>/dev/null)" || fail 'internal smoke request failed'
  else
    response="$(curl "${curl_args[@]}" --insecure --include "https://$SITE_ADDRESS$path" 2>/dev/null)" || fail 'public smoke request failed'
    response="${response//$'\r'/}"
    [[ "$response" == *$'\n\n'* ]] || fail 'public response has no header boundary'
    headers="${response%%$'\n\n'*}"; body="${response#*$'\n\n'}"
    [[ "$(grep -ic "^x-ai-erp-release: $VALUE$" <<<"$headers")" == 1 ]] || fail 'public response release header mismatch'
  fi
  [[ -n "$body" ]] || fail 'smoke response is empty'
  if [[ "$path" == /actuator/health/readiness ]]; then
    python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"' <<<"$body" 2>/dev/null || fail 'readiness is not UP'
  fi
done
printf 'smoke: ok mode=%s\n' "$MODE"
