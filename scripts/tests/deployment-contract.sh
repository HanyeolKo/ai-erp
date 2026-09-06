#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
assert_file() { [ -f "$1" ] || fail "missing file: $1"; }
assert_contains() { grep -Fq -- "$2" "$1" || fail "expected $2 in $1"; }
assert_not_contains() { ! grep -Eq -- "$2" "$1" || fail "unsafe command pattern $2 in $1"; }

for script in deploy preflight smoke rollback backup; do
  assert_file "$ROOT/scripts/$script.sh"
done

for script in "$ROOT"/scripts/{deploy,preflight,smoke,rollback,backup}.sh; do
  bash -n "$script"
  assert_not_contains "$script" '(down[[:space:]]+-v|volume[[:space:]]+prune|volume[[:space:]]+rm|docker[[:space:]]+rm|rm[[:space:]]+-rf[[:space:]]+/|echo[[:space:]]+.*(PASSWORD|SECRET|TOKEN))'
done

assert_contains "$ROOT/scripts/deploy.sh" 'preflight.sh'
assert_contains "$ROOT/scripts/deploy.sh" 'rollback.sh'
assert_contains "$ROOT/scripts/deploy.sh" 'INACTIVE_COLOR'
assert_contains "$ROOT/scripts/deploy.sh" 'manifest.json'
assert_contains "$ROOT/scripts/preflight.sh" '^[a-f0-9]{40}$'
assert_contains "$ROOT/scripts/rollback.sh" 'active-color'
assert_contains "$ROOT/scripts/backup.sh" 'pg_dump'

FAKE_BIN="$TEST_ROOT/bin"
HOST_ROOT="$TEST_ROOT/host"
mkdir -p "$FAKE_BIN" "$HOST_ROOT/shared/caddy" "$HOST_ROOT/releases"
cat >"$HOST_ROOT/shared/.env" <<'ENV'
POSTGRES_DB=ai_erp
POSTGRES_USER=ai_erp
POSTGRES_PASSWORD=not-printed
DB_USERNAME=ai_erp
DB_PASSWORD=not-printed
DB_URL=jdbc:postgresql://postgres:5432/ai_erp
REDIS_URL=redis://redis:6379
SESSION_SECRET=not-printed
OIDC_ENABLED=false
SITE_ADDRESS=192.168.219.100
ENV
chmod 600 "$HOST_ROOT/shared/.env"
cat >"$FAKE_BIN/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${FAKE_DOCKER_LOG:?}"
if [[ "${1:-}" == ps ]]; then exit 0; fi
if [[ "${1:-}" == inspect ]]; then
  if [[ "$*" == *'com.docker.compose.project'* ]]; then printf 'ai-erp-phase1\n'; else printf '%s\n' "${FAKE_HEALTH:-healthy}"; fi
  exit 0
fi
if [[ "${1:-}" == image ]]; then printf 'sha256:fake-image\n'; exit 0; fi
if [[ "${1:-}" != compose ]]; then exit 0; fi
shift
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-name|--env-file|-f|--profile) shift 2 ;;
    version) exit 0 ;;
    up|run|exec|stop) exit 0 ;;
    ps)
      if [[ "$*" == *postgres* ]]; then printf 'fake-postgres\n';
      elif [[ "$*" == *app-blue* ]]; then printf 'fake-blue\n';
      elif [[ "$*" == *app-green* ]]; then printf 'fake-green\n'; fi
      exit 0 ;;
    *) shift ;;
  esac
done
DOCKER
cat >"$FAKE_BIN/curl" <<'CURL'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${FAKE_PUBLIC_FAIL:-0}" == 1 ]]; then exit 22; fi
release="$(sed -n 's/header X-AI-ERP-Release "\([a-f0-9]*\)"/\1/p' "$AI_ERP_ROOT/shared/caddy/active-upstream.caddy")"
if [[ "$*" == *--head* ]]; then printf 'HTTP/1.1 200 OK\r\nX-AI-ERP-Release: %s\r\n\r\n' "$release"; else printf '{"status":"UP"}\n'; fi
CURL
cat >"$FAKE_BIN/flock" <<'FLOCK'
#!/usr/bin/env bash
if [[ "${FAKE_LOCK_HELD:-0}" == 1 ]]; then exit 1; fi
exit 0
FLOCK
cat >"$FAKE_BIN/stat" <<'STAT'
#!/usr/bin/env bash
if [[ "${1:-}" == -c && "${2:-}" == '%a' ]]; then printf '600\n'; else /usr/bin/stat "$@"; fi
STAT
cat >"$FAKE_BIN/df" <<'DF'
#!/usr/bin/env bash
printf 'Filesystem 1024-blocks Used Available Capacity Mounted on\n'
printf 'fake 50000000 1 50000000 1%% /\n'
DF
chmod +x "$FAKE_BIN/docker" "$FAKE_BIN/curl" "$FAKE_BIN/flock" "$FAKE_BIN/stat" "$FAKE_BIN/df"
export PATH="$FAKE_BIN:$PATH" FAKE_DOCKER_LOG="$TEST_ROOT/docker.log" AI_ERP_ROOT="$HOST_ROOT" AI_ERP_PROJECT_DIR="$ROOT" AI_ERP_AVAILABLE_MEM_KIB=3000000 OBSERVATION_SECONDS=0 HEALTH_ATTEMPTS=1 HEALTH_INTERVAL_SECONDS=0

sha_a=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
sha_b=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
sha_c=cccccccccccccccccccccccccccccccccccccccc
sha_d=dddddddddddddddddddddddddddddddddddddddd
"$ROOT/scripts/deploy.sh" "$sha_a"
assert_contains "$HOST_ROOT/releases/$sha_a/manifest.json" '"color":"blue"'
[[ "$(tr -d '[:space:]' < "$HOST_ROOT/shared/active-color")" == blue ]] || fail 'first deployment did not select blue'
[[ "$(tr -d '[:space:]' < "$HOST_ROOT/shared/active-release")" == "$sha_a" ]] || fail 'first deployment did not record release'
"$ROOT/scripts/deploy.sh" "$sha_b"
assert_contains "$HOST_ROOT/releases/$sha_b/manifest.json" '"color":"green"'
[[ "$(tr -d '[:space:]' < "$HOST_ROOT/shared/active-color")" == green ]] || fail 'second deployment did not select green'
[[ "$(tr -d '[:space:]' < "$HOST_ROOT/shared/active-release")" == "$sha_b" ]] || fail 'second deployment did not record release'

FAKE_HEALTH=unhealthy "$ROOT/scripts/deploy.sh" "$sha_c" >/dev/null 2>&1 && fail 'unhealthy inactive slot unexpectedly promoted'
[[ "$(tr -d '[:space:]' < "$HOST_ROOT/shared/active-color")" == green ]] || fail 'pre-switch failure changed active color'
FAKE_PUBLIC_FAIL=1 "$ROOT/scripts/deploy.sh" "$sha_d" >/dev/null 2>&1 && fail 'public smoke failure unexpectedly succeeded'
[[ "$(tr -d '[:space:]' < "$HOST_ROOT/shared/active-color")" == green ]] || fail 'post-switch failure did not roll back'
[[ "$(tr -d '[:space:]' < "$HOST_ROOT/shared/active-release")" == "$sha_b" ]] || fail 'post-switch failure did not restore release'
"$ROOT/scripts/preflight.sh" not-a-sha >/dev/null 2>&1 && fail 'invalid releaseId unexpectedly passed'
FAKE_LOCK_HELD=1 "$ROOT/scripts/preflight.sh" "$sha_a" >/dev/null 2>&1 && fail 'lock contention unexpectedly passed'
assert_not_contains "$FAKE_DOCKER_LOG" '(volume[[:space:]]+(rm|prune)|(^|[[:space:]])rm[[:space:]]|stop[[:space:]]+[^a])'

printf 'PASS: deployment script static contract\n'
