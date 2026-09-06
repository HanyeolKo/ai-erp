#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
SITE_ADDRESS="192.168.219.100"
TEMP_ROOT="${TMPDIR:-/tmp}"
TEST_DIR="$(mktemp -d "$TEMP_ROOT/ai-erp-caddy-no-sni.XXXXXX")"
CONTAINER="ai-erp-caddy-no-sni-$$-${RANDOM}"

cleanup() {
  docker rm --force "$CONTAINER" >/dev/null 2>&1 || true
  case "$TEST_DIR" in
    "$TEMP_ROOT"/ai-erp-caddy-no-sni.*)
      rm -f -- "$TEST_DIR/state/active-upstream.caddy"
      rmdir -- "$TEST_DIR/state" "$TEST_DIR"
      ;;
    *)
      printf 'FAIL: refusing to clean unexpected test directory\n' >&2
      return 1
      ;;
  esac
}
trap cleanup EXIT

command -v docker >/dev/null || { printf 'FAIL: docker is required\n' >&2; exit 1; }
command -v openssl >/dev/null || { printf 'FAIL: openssl is required\n' >&2; exit 1; }
command -v timeout >/dev/null || { printf 'FAIL: timeout is required\n' >&2; exit 1; }

mkdir "$TEST_DIR/state"
printf 'respond "ready" 200\n' >"$TEST_DIR/state/active-upstream.caddy"

docker run --detach --rm \
  --name "$CONTAINER" \
  --publish '127.0.0.1::443' \
  --env "SITE_ADDRESS=$SITE_ADDRESS" \
  --mount "type=bind,src=$PROJECT/infra,dst=/etc/caddy/source,readonly" \
  --mount "type=bind,src=$TEST_DIR/state,dst=/etc/caddy/state,readonly" \
  --tmpfs /data:rw,noexec,nosuid,nodev,size=16m \
  --tmpfs /config:rw,noexec,nosuid,nodev,size=4m \
  --entrypoint caddy \
  caddy:2.11.4 \
  run --config /etc/caddy/source/Caddyfile --adapter caddyfile >/dev/null

MAPPED_PORT=""
for _ in $(seq 1 20); do
  MAPPED_PORT="$(docker port "$CONTAINER" 443/tcp 2>/dev/null || true)"
  [[ "$MAPPED_PORT" == 127.0.0.1:* ]] && break
  sleep 0.25
done
[[ "$MAPPED_PORT" =~ ^127\.0\.0\.1:([0-9]+)$ ]] || { printf 'FAIL: TLS port is not bound to loopback\n' >&2; exit 1; }
HOST_PORT="${BASH_REMATCH[1]}"

request() {
  local sni_mode="$1"
  local -a sni_args=(-noservername)
  if [[ "$sni_mode" == "with-sni" ]]; then
    sni_args=(-servername "$SITE_ADDRESS")
  fi
  printf 'GET / HTTP/1.1\r\nHost: %s\r\nConnection: close\r\n\r\n' "$SITE_ADDRESS" |
    timeout 3 openssl s_client \
      -connect "127.0.0.1:$HOST_PORT" \
      "${sni_args[@]}" \
      -quiet 2>/dev/null || true
}

READY_RESPONSE=""
for _ in $(seq 1 20); do
  READY_RESPONSE="$(request with-sni)"
  if grep -q '^HTTP/1\.[01] 200' <<<"$READY_RESPONSE"; then
    break
  fi
  sleep 0.25
done
grep -q '^HTTP/1\.[01] 200' <<<"$READY_RESPONSE" || { printf 'FAIL: Caddy did not become ready with explicit SNI\n' >&2; exit 1; }

NO_SNI_RESPONSE="$(request without-sni)"
grep -q '^HTTP/1\.[01] 200' <<<"$NO_SNI_RESPONSE" || {
  printf 'FAIL: no-SNI TLS request with the configured Host did not return HTTP 200\n' >&2
  exit 1
}

printf 'PASS: 1 scenario / 3 assertions (loopback, explicit-SNI readiness, no-SNI HTTP 200)\n'
