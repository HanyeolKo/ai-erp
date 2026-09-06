#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"; T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
fail(){ printf 'FAIL: %s\n' "$*" >&2; exit 1; }; ok(){ "$@" || fail "$*"; }; id="sha256:$(printf 'a%.0s' {1..64})"
for f in "$ROOT"/scripts/{lib-deploy,deploy,preflight,smoke,rollback,backup}.sh; do bash -n "$f"; done
BIN="$T/bin"; HOST="$T/host"; mkdir -p "$BIN" "$HOST/shared/caddy" "$HOST/releases"
printf '%s\n' 'POSTGRES_DB=ai_erp' 'POSTGRES_USER=ai_erp' 'POSTGRES_PASSWORD=not-printed-password' 'DB_USERNAME=ai_erp' 'DB_PASSWORD=not-printed-password' 'REDIS_PASSWORD=abcdefghijklmnopqrstuvwx' 'DB_URL=jdbc:postgresql://postgres:5432/ai_erp' 'REDIS_URL=redis://:abcdefghijklmnopqrstuvwx@redis:6379/0' 'SESSION_SECRET=not-printed-session' 'APP_OIDC_ENABLED=false' 'GOOGLE_CLIENT_ID=' 'GOOGLE_CLIENT_SECRET=' 'SITE_ADDRESS=192.168.219.100' >"$HOST/shared/.env"; chmod 600 "$HOST/shared/.env"
printf '%s\n' '#!/usr/bin/env bash' 'set -Eeuo pipefail' 'printf "%s\n" "$*" >>"$FAKE_LOG"' 'fake_id(){ printf "sha256:%s%024d" "$1" 0; }' 'if [[ "$1" == build ]]; then exit 0; fi' 'if [[ "$1" == image ]]; then tag=$(printf "%s" "$*" | sed -n "s/.*ai-erp:\([a-f0-9]\{40\}\).*/\1/p"); [[ -n "$tag" ]] || tag=$(printf "%s" "$*" | sed -n "s/.*sha256:\([a-f0]\{40\}\).*/\1/p"); printf "%s %s\n" "$(fake_id "$tag")" "$tag"; exit; fi' 'if [[ "$1" == inspect ]]; then [[ "$*" == *com.docker.compose.project* ]] && printf ai-erp-phase1 || { [[ "$*" == *"{{.Image}}"* ]] && printf "%s\n" "$(fake_id "$FAKE_HEAD")" || printf "%s" "${FAKE_HEALTH:-healthy}"; }; exit; fi' 'if [[ "$1" == ps ]]; then exit; fi' '[[ "$1" == compose ]] || exit 0; shift' 'while [[ $# -gt 0 ]]; do case "$1" in --project-name|--env-file|-f|--profile) shift 2;; version|config|up|run|stop) exit;; exec) if [[ "$*" == *wget* ]]; then printf "{\"status\":\"UP\"}"; fi; if [[ "$*" == *pg_dump* ]]; then printf dump; fi; if [[ "$*" == *psql* ]]; then printf t; fi; exit;; ps) if [[ "$*" == *app-blue* ]]; then printf blue; elif [[ "$*" == *app-green* ]]; then printf green; else printf postgres; fi; exit;; *) shift;; esac; done' >"$BIN/docker"
printf '%s\n' '#!/usr/bin/env bash' 'set -e' 'case "$*" in *"rev-parse HEAD"*) printf "%s\n" "$FAKE_HEAD";; *"rev-parse --verify"*) printf "%s\n" "$FAKE_HEAD";; *status*) printf "%s" "${FAKE_DIRTY:-}";; esac' >"$BIN/git"
printf '%s\n' '#!/usr/bin/env bash' '[[ "${FAKE_CURL_FAIL:-0}" == 1 ]] && exit 22' 'r=$(sed -n "s/header X-AI-ERP-Release \"\([a-f0-9]*\)\"/\1/p" "$AI_ERP_ROOT/shared/caddy/active-upstream.caddy")' '[[ "$*" == *--head* ]] && printf "HTTP/1.1 200 OK\r\nX-AI-ERP-Release: %s\r\n\r\n" "$r" || printf "{\"status\":\"UP\"}\n"' >"$BIN/curl"
printf '%s\n' '#!/usr/bin/env bash' '[[ "${FAKE_LOCK:-0}" == 1 ]] && exit 1; exit 0' >"$BIN/flock"
printf '%s\n' '#!/usr/bin/env bash' '[[ "$1" == -c ]] && { printf "600\n"; exit; }; /usr/bin/stat "$@"' >"$BIN/stat"
printf '%s\n' '#!/usr/bin/env bash' 'printf "Filesystem blocks Used Available Capacity Mounted\n"' 'printf "f 99999999 1 99999999 1%% /\n"' >"$BIN/df"
printf '%s\n' '#!/usr/bin/env bash' 'exit 1' >"$BIN/ss"
chmod +x "$BIN"/*
export PATH="$BIN:$PATH" FAKE_LOG="$T/docker.log" FAKE_ID="$id" AI_ERP_ROOT="$HOST" AI_ERP_PROJECT_DIR="$ROOT" AI_ERP_TEST_MODE=1 AI_ERP_AVAILABLE_MEM_KIB=3000000 OBSERVATION_SECONDS=0 HEALTH_ATTEMPTS=1 HEALTH_INTERVAL_SECONDS=0
a=$(printf 'a%.0s' {1..40}); b=$(printf 'b%.0s' {1..40}); c=$(printf 'c%.0s' {1..40}); d=$(printf 'd%.0s' {1..40})
FAKE_HEAD="$a" "$ROOT/scripts/deploy.sh" "$a"; grep -Fq '"color":"blue"' "$HOST/releases/$a/manifest.json" || fail first
FAKE_HEAD="$b" "$ROOT/scripts/deploy.sh" "$b"; grep -Fq '"color":"green"' "$HOST/shared/active-state.json" || fail switch
FAKE_HEAD="$c" FAKE_HEALTH=unhealthy "$ROOT/scripts/deploy.sh" "$c" >/dev/null 2>&1 && fail pre-switch; grep -Fq "$b" "$HOST/shared/active-state.json" || fail preserve
FAKE_HEAD="$d" FAKE_CURL_FAIL=1 "$ROOT/scripts/deploy.sh" "$d" >/dev/null 2>&1 && fail post-switch; grep -Fq "$b" "$HOST/shared/active-state.json" || fail rollback
FAKE_HEAD="$a" "$ROOT/scripts/rollback.sh"; grep -Fq "$a" "$HOST/shared/active-state.json" || fail no-arg-rollback
FAKE_HEAD="$b" "$ROOT/scripts/rollback.sh" "$b"; grep -Fq "$b" "$HOST/shared/active-state.json" || fail explicit-rollback
FAKE_HEAD="$a" "$ROOT/scripts/preflight.sh" AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA >/dev/null 2>&1 && fail uppercase
FAKE_HEAD="$a" FAKE_DIRTY=x "$ROOT/scripts/preflight.sh" "$a" >/dev/null 2>&1 && fail dirty
FAKE_HEAD="$b" "$ROOT/scripts/preflight.sh" "$a" >/dev/null 2>&1 && fail wrong-head
FAKE_HEAD="$a" FAKE_LOCK=1 "$ROOT/scripts/preflight.sh" "$a" >/dev/null 2>&1 && fail lock
! grep -Eq 'not-printed|volume (rm|prune)| down -v|stop app-[^bg]' "$FAKE_LOG" || fail unsafe-or-secret
grep -q 'build .*org.opencontainers.image.revision' "$FAKE_LOG" && grep -q 'run .*caddy validate' "$FAKE_LOG" && grep -q 'stop app-' "$FAKE_LOG" || fail missing-order-events
printf 'PASS: stateful deployment contract\n'
