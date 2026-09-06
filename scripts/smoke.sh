#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-internal}"
COLOR="${2:-}"
ROOT="${AI_ERP_ROOT:-/home/deploy/ai-erp}"
PROJECT_DIR="${AI_ERP_PROJECT_DIR:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)}"
ENV_FILE="$ROOT/shared/.env"
COMPOSE=(docker compose --project-name ai-erp-phase1 --env-file "$ENV_FILE" -f "$PROJECT_DIR/infra/compose.prod.yml")

fail() { printf 'smoke: %s\n' "$*" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || fail 'shared environment file is missing'
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

case "$MODE" in
  internal)
    [[ "$COLOR" == blue || "$COLOR" == green ]] || fail 'internal smoke requires blue or green'
    container_id="$("${COMPOSE[@]}" ps -q "app-$COLOR")"
    [[ -n "$container_id" ]] || fail 'application slot is not running'
    status="$(docker inspect -f '{{.State.Health.Status}}' "$container_id")"
    [[ "$status" == healthy ]] || fail 'application slot is not healthy'
    ;;
  public)
    release_id="${COLOR:-}"
    [[ "$release_id" =~ ^[a-f0-9]{40}$ ]] || fail 'public smoke requires releaseId'
    headers="$(curl --silent --show-error --fail --insecure --head "https://${SITE_ADDRESS}/actuator/health/readiness")"
    tr -d '\r' <<<"$headers" | grep -qi "^x-ai-erp-release: $release_id$" || fail 'public response does not identify the requested release'
    curl --silent --show-error --fail --insecure "https://${SITE_ADDRESS}/actuator/health/readiness" | grep -q '"status":"UP"' || fail 'public readiness is not UP'
    ;;
  *) fail 'mode must be internal or public' ;;
esac
printf 'smoke: ok mode=%s\n' "$MODE"
