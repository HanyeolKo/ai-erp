#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
SITE_ADDRESS="ai-erp.duckdns.org"
PRODUCTION_IP="192.168.219.100"
TEST_RELEASE="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
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
command -v curl >/dev/null || { printf 'FAIL: curl is required\n' >&2; exit 1; }

mkdir "$TEST_DIR/state"
printf 'header X-AI-ERP-Release "%s"\nrespond "ready" 200\n' "$TEST_RELEASE" >"$TEST_DIR/state/active-upstream.caddy"

docker run --detach --rm \
  --name "$CONTAINER" \
  --publish '127.0.0.1::443' \
  --publish '127.0.0.1::80' \
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
  local sni_mode="$1" site="${2:-$PRODUCTION_IP}" path="${3:-/actuator/health/readiness}"
  local -a sni_args=(-noservername)
  if [[ "$sni_mode" == "with-sni" ]]; then
    sni_args=(-servername "$site")
  fi
  printf 'GET %s HTTP/1.1\r\nHost: %s\r\nConnection: close\r\n\r\n' "$path" "$site" |
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

for site in ai-erp.duckdns.org blackcow.duckdns.org; do
  for path in / /oauth2/authorization/google; do
    DOMAIN_RESPONSE="$(request with-sni "$site" "$path")"
    grep -q '^HTTP/1\.[01] 200' <<<"$DOMAIN_RESPONSE" || {
      printf 'FAIL: approved domain must reach upstream with its own origin\n' >&2
      exit 1
    }
  done
done

for path in '/?entry=preserve' '/index.html?entry=preserve'; do
  IP_ENTRY_RESPONSE="$(request without-sni "$PRODUCTION_IP" "$path")"
  IP_ENTRY_RESPONSE="${IP_ENTRY_RESPONSE//$'\r'/}"
  grep -q '^HTTP/1\.[01] 302' <<<"$IP_ENTRY_RESPONSE" || {
    printf 'FAIL: IP browser entry must redirect before the SPA stores invitation state\n' >&2
    exit 1
  }
  grep -Fxiq "location: https://ai-erp.duckdns.org$path" <<<"$IP_ENTRY_RESPONSE" || {
    printf 'FAIL: IP entry must preserve URI without a Location fragment so browsers inherit invitation fragments\n' >&2
    exit 1
  }
done
IP_DOCS_RESPONSE="$(request without-sni "$PRODUCTION_IP" /assets/api-docs/index.html)"
grep -q '^HTTP/1\.[01] 200' <<<"$IP_DOCS_RESPONSE" || {
  printf 'FAIL: IP documentation must continue reaching upstream\n' >&2
  exit 1
}

IP_LOGIN_RESPONSE="$(request without-sni "$PRODUCTION_IP" '/oauth2/authorization/google?test=preserve')"
IP_LOGIN_RESPONSE="${IP_LOGIN_RESPONSE//$'\r'/}"
grep -q '^HTTP/1\.[01] 302' <<<"$IP_LOGIN_RESPONSE" || {
  printf 'FAIL: IP login initiation must redirect before upstream handles it\n' >&2
  exit 1
}
grep -Fxiq 'location: https://ai-erp.duckdns.org/oauth2/authorization/google?test=preserve' <<<"$IP_LOGIN_RESPONSE" || {
  printf 'FAIL: IP login redirect must preserve the URI on the primary domain\n' >&2
  exit 1
}
grep -Fxiq "x-ai-erp-release: $TEST_RELEASE" <<<"$IP_LOGIN_RESPONSE" || {
  printf 'FAIL: IP login redirect must retain the release header required by public smoke\n' >&2
  exit 1
}
IP_CALLBACK_RESPONSE="$(request without-sni "$PRODUCTION_IP" /login/oauth2/code/google)"
grep -q '^HTTP/1\.[01] 200' <<<"$IP_CALLBACK_RESPONSE" || {
  printf 'FAIL: IP callback must continue reaching upstream\n' >&2
  exit 1
}

MAPPED_HTTP_PORT="$(docker port "$CONTAINER" 80/tcp)"
[[ "$MAPPED_HTTP_PORT" =~ ^127\.0\.0\.1:([0-9]+)$ ]] || { printf 'FAIL: HTTP port is not bound to loopback\n' >&2; exit 1; }
HTTP_PORT="${BASH_REMATCH[1]}"
for site in 203.0.113.9 ai-erp.duckdns.org blackcow.duckdns.org unapproved.example; do
  HTTP_RESPONSE="$(curl --silent --show-error --max-time 3 --noproxy '*' --include --header "Host: $site" "http://127.0.0.1:$HTTP_PORT/projects?test=preserve")"
  HTTP_RESPONSE="${HTTP_RESPONSE//$'\r'/}"
  grep -q '^HTTP/1\.[01] 308' <<<"$HTTP_RESPONSE" || {
    printf 'FAIL: HTTP entry must redirect raw-IP and domain visitors\n' >&2
    exit 1
  }
  grep -Fxiq 'location: https://ai-erp.duckdns.org/projects?test=preserve' <<<"$HTTP_RESPONSE" || {
    printf 'FAIL: HTTP redirect must use the fixed approved HTTPS origin and preserve the URI\n' >&2
    exit 1
  }
done

printf 'PASS: 1 scenario / 25 assertions (loopback, IP SNI/no-SNI, both domain origins, canonical browser entry, bounded IP OAuth redirect, canonical HTTP entry)\n'
