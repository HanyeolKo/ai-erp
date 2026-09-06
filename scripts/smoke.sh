#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=smoke
source "$(dirname -- "$0")/lib-deploy.sh"
MODE="${1:-internal}"; VALUE="${2:-}"
load_env
case "$MODE" in
internal)
  [[ "$VALUE" == blue || "$VALUE" == green ]] || fail 'internal smoke requires blue or green'
  "${COMPOSE[@]}" exec -T "app-$VALUE" wget -q -O - http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"' || fail 'candidate readiness is not UP'
  ;;
public)
  valid_release "$VALUE" || fail 'public smoke requires releaseId'
  headers="$(curl --silent --show-error --fail --insecure --head "https://${SITE_ADDRESS}/actuator/health/readiness")"
  tr -d '\r' <<<"$headers" | grep -qi "^x-ai-erp-release: $VALUE$" || fail 'public response does not identify the requested release'
  curl --silent --show-error --fail --insecure "https://${SITE_ADDRESS}/actuator/health/readiness" | grep -q '"status":"UP"' || fail 'public readiness is not UP'
  ;;
*) fail 'mode must be internal or public';; esac
printf 'smoke: ok mode=%s\n' "$MODE"
