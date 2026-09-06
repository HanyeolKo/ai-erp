#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
DEPLOY_MAIN_PID="$BASHPID"

ROOT="${AI_ERP_ROOT:-/home/deploy/ai-erp}"
PROJECT_DIR="${AI_ERP_PROJECT_DIR:-$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}"
ENV_FILE="$ROOT/shared/.env"
STATE_DIR="$ROOT/shared/caddy"
STATE_FILE="$ROOT/shared/active-state.json"
UPSTREAM_FILE="$STATE_DIR/active-upstream.caddy"
LOCK_FILE="$ROOT/shared/deploy.lock"
COMPOSE_FILE="$PROJECT_DIR/infra/compose.prod.yml"
MIGRATION_DIR="$PROJECT_DIR/backend/src/main/resources/db/migration"
COMPOSE=(docker compose --project-name ai-erp-phase1 --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
TRANSACTION=0 SWITCH_ATTEMPTED=0 CANDIDATE_STARTED=0 NEW_MANIFEST=0 NEW_MANIFEST_CHECKSUM=0
EVENT_FILE="" RELEASE_DIR="" CANDIDATE_COLOR=""

fail() { printf '%s: %s\n' "${SCRIPT_NAME:-deploy}" "$*" >&2; return 1; }
valid_release() { [[ "$1" =~ ^[a-f0-9]{40}$ ]]; }
valid_image() { [[ "$1" =~ ^sha256:[a-f0-9]{64}$ ]]; }
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
sha256() { sha256sum "$1" | awk '{print $1}'; }
text_checksum() { printf '%s' "$1" | sha256sum | awk '{print $1}'; }

no_symlinks() {
  local path="$1" component="$1" canonical
  while [[ "$component" != / && "$component" != . && "$component" != "${component%/*}" ]]; do
    [[ ! -L "$component" ]] || fail 'symbolic links are forbidden in deployment paths'
    component="${component%/*}"
    [[ -n "$component" ]] || break
  done
  canonical="$(readlink -f "$path")" || fail 'deployment path cannot be resolved'
  [[ "$canonical" == "$path" ]] || fail 'deployment paths must be absolute and canonical'
}
secure_dir() {
  no_symlinks "$1"
  [[ -d "$1" && "$(stat -c '%u' "$1")" == "$(id -u)" && "$(stat -c '%a' "$1")" == 700 ]] || fail 'deployment directory must be owned by deploy user with mode 700'
}
secure_file() {
  no_symlinks "$1"
  [[ -f "$1" && "$(stat -c '%u' "$1")" == "$(id -u)" && "$(stat -c '%a' "$1")" == 600 ]] || fail 'deployment file must be regular, owned by deploy user with mode 600'
}
optional_file() { if [[ -e "$1" || -L "$1" ]]; then secure_file "$1"; fi; }
validate_base() {
  local path command
  if [[ "${AI_ERP_TEST_MODE:-0}" != 1 ]]; then
    [[ "$ROOT" == /home/deploy/ai-erp && "$PROJECT_DIR" == /home/deploy/actions-runner-ai-erp/_work/ai-erp/ai-erp ]] || fail 'unexpected production root or checkout'
  fi
  for command in docker git flock ss stat readlink id df awk sed grep find wc tr sha256sum python3 curl date cp mv ln rm chmod mkdir sleep touch; do
    command -v "$command" >/dev/null || fail "required command unavailable: $command"
  done
  for path in "$ROOT" "$ROOT/shared" "$ROOT/releases" "$STATE_DIR"; do secure_dir "$path"; done
  no_symlinks "$PROJECT_DIR"
  secure_file "$ENV_FILE"
  optional_file "$LOCK_FILE"
  optional_file "$STATE_FILE"
  optional_file "$UPSTREAM_FILE"
}
verify_no_transaction() {
  local path
  for path in "$STATE_DIR/candidate.Caddyfile" "$STATE_DIR/upstream.candidate" "$STATE_DIR/state.candidate" "$STATE_DIR/upstream.previous" "$STATE_DIR/state.previous" "$STATE_DIR/upstream.restore" "$STATE_DIR/state.restore"; do
    [[ ! -e "$path" && ! -L "$path" ]] || fail 'unfinished transaction exists; inspect preserved recovery files before retry'
  done
}
acquire_lock() {
  local inherited="" expected
  expected="$(readlink -f "$LOCK_FILE")"
  # fd 9 is trusted only when it refers to this exact verified lock inode/path.
  # Re-flocking an inherited open file description preserves the parent's lock.
  if inherited="$(readlink -f "/proc/$$/fd/9" 2>/dev/null)" && [[ "$inherited" == "$expected" ]]; then
    flock -n 9 || fail 'another deployment holds the lock'
  else
    exec 9>>"$LOCK_FILE"
    chmod 600 "$LOCK_FILE"
    flock -n 9 || fail 'another deployment holds the lock'
  fi
  secure_file "$LOCK_FILE"
}
load_env() {
  local line key value required
  local -A seen=()
  # .env is data, never executable shell. Unknown keys and shell interpolation
  # fail closed; APP_IMAGE is deliberately ignored and re-derived by the caller.
  for key in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD DB_USERNAME DB_PASSWORD DB_URL REDIS_URL REDIS_PASSWORD APP_OIDC_ENABLED GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET SITE_ADDRESS; do
    unset "$key"
  done
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" =~ ^([A-Z_][A-Z_0-9]*)=(.*)$ ]] || fail 'environment syntax is invalid'
    key="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[2]}"
    [[ "$key" == APP_IMAGE ]] && continue
    case "$key" in POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD|DB_USERNAME|DB_PASSWORD|DB_URL|REDIS_URL|REDIS_PASSWORD|APP_OIDC_ENABLED|GOOGLE_CLIENT_ID|GOOGLE_CLIENT_SECRET|SITE_ADDRESS) :;; *) fail 'unknown environment key';; esac
    [[ ! -v "seen[$key]" ]] || fail 'duplicate environment key'
    seen[$key]=1
    if [[ "$value" == \"*\" || "$value" == \'*\' ]]; then value="${value:1:${#value}-2}"; fi
    [[ "$value" != *'$'* && "$value" != *'`'* && "$value" != *'"'* && "$value" != *"'"* && "$value" != *'\'* && "$value" != *[[:space:]]* ]] || fail 'environment values must be literal single-line tokens'
    printf -v "$key" '%s' "$value"
    export "$key"
  done <"$ENV_FILE"
  for required in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD DB_USERNAME DB_PASSWORD DB_URL REDIS_URL REDIS_PASSWORD APP_OIDC_ENABLED SITE_ADDRESS; do
    [[ -n "${!required:-}" ]] || fail "required environment key missing: $required"
  done
  [[ "$POSTGRES_DB" == ai_erp && "$POSTGRES_USER" =~ ^[a-z_][a-z0-9_]*$ ]] || fail 'project database must be ai_erp with a safe role name'
  [[ "$DB_USERNAME" == "$POSTGRES_USER" && "$DB_PASSWORD" == "$POSTGRES_PASSWORD" ]] || fail 'database credentials must match project PostgreSQL'
  [[ "$DB_URL" == "jdbc:postgresql://postgres:5432/$POSTGRES_DB" ]] || fail 'DB_URL must target project PostgreSQL exactly'
  [[ "$REDIS_PASSWORD" =~ ^[A-Za-z0-9._~-]{24,128}$ && "$REDIS_URL" == "redis://:$REDIS_PASSWORD@redis:6379/0" ]] || fail 'Redis must use the exact project URL and URL-safe password'
  [[ "$SITE_ADDRESS" == 192.168.219.100 ]] || fail 'SITE_ADDRESS must equal the approved production IP'
  [[ "$APP_OIDC_ENABLED" == true || "$APP_OIDC_ENABLED" == false ]] || fail 'APP_OIDC_ENABLED must be true or false'
  [[ ( -z "${GOOGLE_CLIENT_ID:-}" && -z "${GOOGLE_CLIENT_SECRET:-}" ) || ( -n "${GOOGLE_CLIENT_ID:-}" && -n "${GOOGLE_CLIENT_SECRET:-}" ) ]] || fail 'Google credentials must be a complete pair'
  if [[ "$APP_OIDC_ENABLED" == true ]]; then [[ -n "${GOOGLE_CLIENT_ID:-}" && -n "${GOOGLE_CLIENT_SECRET:-}" ]] || fail 'OIDC requires Google credentials'; fi
  export CADDY_STATE_DIR="$STATE_DIR" RELEASE_SOURCE_DIR="$PROJECT_DIR"
}
verify_source() {
  git -C "$PROJECT_DIR" rev-parse --verify 'HEAD^{commit}' >/dev/null 2>&1 || fail 'repository HEAD is not a commit'
  [[ "$(git -C "$PROJECT_DIR" rev-parse HEAD)" == "$RELEASE_ID" ]] || fail 'releaseId does not match repository HEAD'
  [[ -z "$(git -C "$PROJECT_DIR" status --porcelain=v1 --untracked-files=all)" ]] || fail 'tracked or nonignored untracked worktree changes exist'
}
verify_migrations() {
  local path
  for path in "$MIGRATION_DIR"/V1__create_module_schemas.sql "$MIGRATION_DIR"/V2__create_phase1_tables.sql "$MIGRATION_DIR"/V4__add_phase1_concurrency_guards.sql "$MIGRATION_DIR"/V5__add_bounded_read_indexes.sql; do
    no_symlinks "$path"
    [[ -s "$path" && -f "$path" ]] || fail 'required migration file missing'
  done
  [[ "$(find "$MIGRATION_DIR" -mindepth 1 -maxdepth 1 | wc -l)" == 4 ]] || fail 'unexpected migration set'
}
migration_checksum() {
  (cd "$MIGRATION_DIR" && sha256sum V1__create_module_schemas.sql V2__create_phase1_tables.sql V4__add_phase1_concurrency_guards.sql V5__add_bounded_read_indexes.sql) | sha256sum | awk '{print $1}'
}
verify_resources() {
  local kind list name label expected
  for kind in volume network; do
    list="$(docker "$kind" ls --format '{{.Name}}')"
    local -a names=(ai_erp_phase1_postgres_data ai_erp_phase1_redis_data ai_erp_phase1_caddy_data ai_erp_phase1_caddy_config)
    [[ "$kind" != network ]] || names=(ai-erp-phase1-internal)
    for name in "${names[@]}"; do
      if grep -Fxq "$name" <<<"$list"; then
        expected="$name"; [[ "$kind" != network ]] || expected=internal
        label="$(docker "$kind" inspect --format "{{ index .Labels \"com.docker.compose.project\" }} {{ index .Labels \"com.docker.compose.$kind\" }}" "$name")"
        [[ "$label" == "ai-erp-phase1 $expected" ]] || fail 'named Docker resource has foreign or missing Compose ownership'
      fi
    done
  done
}
verify_ports() {
  local port listeners publishers container labels count
  for port in 80 443; do
    listeners="$(ss -ltn "sport = :$port")"
    publishers="$(docker ps -q --filter "publish=$port")"
    count=0
    while IFS= read -r container; do
      [[ -n "$container" ]] || continue
      labels="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }} {{ index .Config.Labels "com.docker.compose.service" }}' "$container")"
      [[ "$labels" == 'ai-erp-phase1 caddy' ]] || fail 'public port has a foreign Docker publisher'
      [[ "$("${COMPOSE[@]}" ps -q caddy)" == "$container" ]] || fail 'publisher is not the exact project Caddy container'
      count=$((count + 1))
    done <<<"$publishers"
    if grep -q LISTEN <<<"$listeners"; then [[ "$count" == 1 ]] || fail 'host listener has no exact project Caddy publisher'; fi
  done
}
verify_host() {
  docker info --format '{{.ServerVersion}}' >/dev/null 2>&1 || fail 'Docker daemon unavailable'
  docker compose version --short >/dev/null 2>&1 || fail 'Docker Compose unavailable'
  verify_resources
  verify_ports
  [[ "$(df -Pk "$ROOT" | awk 'NR==2 {print $4}')" -ge 20971520 ]] || fail 'free disk is below 20 GiB'
  local available
  available="$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)"
  if [[ "${AI_ERP_TEST_MODE:-0}" == 1 ]]; then available="${AI_ERP_AVAILABLE_MEM_KIB:-$available}"; fi
  [[ "$available" =~ ^[0-9]+$ && "$available" -ge 2621440 ]] || fail 'available memory is below 2.5 GiB'
  "${COMPOSE[@]}" config --quiet >/dev/null 2>&1 || fail 'production Compose config is invalid'
}
json_write() {
  python3 - "$@" <<'PY'
import json,sys
sys.stdout.reconfigure(newline='\n')
a=sys.argv[1:]
assert len(a)%2 == 0
print(json.dumps(dict(zip(a[::2],a[1::2])),separators=(',',':')))
PY
}
json_fields() {
  python3 - "$@" <<'PY'
import json,sys
sys.stdout.reconfigure(newline='\n')
def unique(pairs):
    d={}
    for k,v in pairs:
        if k in d: raise ValueError('duplicate key')
        d[k]=v
    return d
try:
    with open(sys.argv[1],encoding='utf-8') as f: d=json.load(f,object_pairs_hook=unique)
    for key in sys.argv[2:]:
        v=d[key]
        if not isinstance(v,str) or any(ord(c)<32 for c in v): raise ValueError('invalid value')
        print(v)
except Exception:
    print('invalid deployment metadata',file=sys.stderr)
    sys.exit(1)
PY
}
image_identity() {
  local details
  details="$(docker image inspect --format '{{.Id}} {{ index .Config.Labels "org.opencontainers.image.revision" }}' "$1" 2>/dev/null)" || fail 'local immutable image is missing'
  IMAGE_ID="${details%% *}" IMAGE_REVISION="${details#* }"
  valid_image "$IMAGE_ID" && [[ "$IMAGE_REVISION" == "$2" ]] || fail 'image identity or OCI revision label mismatch'
}
validate_manifest() {
  local release="$1" file="$ROOT/releases/$1/manifest.json" fields digest
  valid_release "$release" || fail 'invalid manifest release'
  secure_dir "$ROOT/releases/$release"
  secure_file "$file"; secure_file "$file.sha256"
  digest="$(<"$file.sha256")"
  [[ "$digest" =~ ^[a-f0-9]{64}$ && "$(sha256 "$file")" == "$digest" ]] || fail 'immutable manifest checksum mismatch'
  fields="$(json_fields "$file" releaseId revision imageId imageChecksum color previousRelease previousColor startedAt migratedAt switchedAt completedAt composeChecksum caddyChecksum migrationChecksum migrationResult)"
  local -a values
  mapfile -t values <<<"$fields"
  [[ "${values[0]}" == "$release" && "${values[1]}" == "$release" ]] || fail 'manifest revision mismatch'
  valid_image "${values[2]}" && [[ "${values[3]}" == "$(text_checksum "${values[2]}")" ]] || fail 'manifest image checksum mismatch'
  [[ "${values[4]}" == blue || "${values[4]}" == green ]] || fail 'manifest color invalid'
  [[ ( -z "${values[5]}" && -z "${values[6]}" ) || ( "${values[5]}" =~ ^[a-f0-9]{40}$ && "${values[6]}" =~ ^(blue|green)$ ) ]] || fail 'manifest lineage invalid'
  local i
  for i in 7 8 9 10; do [[ "${values[$i]}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || fail 'manifest timestamp invalid'; done
  for i in 11 12 13; do [[ "${values[$i]}" =~ ^[a-f0-9]{64}$ ]] || fail 'manifest artifact checksum invalid'; done
  [[ "${values[14]}" == success ]] || fail 'manifest migration did not succeed'
  image_identity "${values[2]}" "$release"
  [[ "$IMAGE_ID" == "${values[2]}" ]] || fail 'manifest image identity differs from local image'
  image_identity "ai-erp:$release" "$release"
  [[ "$IMAGE_ID" == "${values[2]}" ]] || fail 'release tag differs from preserved immutable image'
  MANIFEST_IMAGE="$IMAGE_ID" MANIFEST_CHECKSUM="$digest"
}
verify_container() {
  local service="$1" expected="$2" validation_mode="${3:-strict}" container labels
  [[ "$validation_mode" == strict || "$validation_mode" == recovery ]] || fail 'invalid container validation mode'
  if [[ "$validation_mode" == recovery ]]; then
    container="$("${COMPOSE[@]}" ps --all -q "$service")"
    [[ -n "$container" ]] || return 0
  else container="$("${COMPOSE[@]}" ps -q "$service")"; fi
  [[ -n "$container" && "$container" != *$'\n'* ]] || fail 'expected one running project application container'
  labels="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }} {{ index .Config.Labels "com.docker.compose.service" }}' "$container")"
  [[ "$labels" == "ai-erp-phase1 $service" ]] || fail 'application container ownership mismatch'
  if [[ "$validation_mode" == strict ]]; then
    [[ "$(docker inspect -f '{{.State.Health.Status}}' "$container")" == healthy ]] || fail 'application container is unhealthy'
  fi
  [[ "$(docker inspect -f '{{.Image}}' "$container")" == "$expected" ]] || fail 'application container image differs from immutable image'
}
load_state() {
  local validation_mode="${1:-strict}"
  [[ "$validation_mode" == strict || "$validation_mode" == recovery ]] || fail 'invalid state validation mode'
  STATE_COLOR="" STATE_RELEASE="" STATE_IMAGE_ID="" STATE_PREVIOUS_RELEASE="" STATE_PREVIOUS_COLOR="" STATE_MANIFEST_CHECKSUM=""
  if [[ ! -e "$STATE_FILE" ]]; then
    if [[ -e "$UPSTREAM_FILE" ]]; then
      [[ "$(<"$UPSTREAM_FILE")" == 'respond "service initializing" 503' ]] || fail 'missing state with non-initial upstream'
    fi
    local slot
    for slot in blue green; do [[ -z "$("${COMPOSE[@]}" ps -q "app-$slot")" ]] || fail 'missing state with a running application'; done
    return 0
  fi
  secure_file "$STATE_FILE"; secure_file "$UPSTREAM_FILE"
  local fields expected saved_image saved_checksum
  local -a values
  fields="$(json_fields "$STATE_FILE" releaseId color imageId revision previousRelease previousColor manifestChecksum previousManifestChecksum)"
  mapfile -t values <<<"$fields"
  STATE_RELEASE="${values[0]}" STATE_COLOR="${values[1]}" STATE_IMAGE_ID="${values[2]}" STATE_PREVIOUS_RELEASE="${values[4]}" STATE_PREVIOUS_COLOR="${values[5]}" STATE_MANIFEST_CHECKSUM="${values[6]}"
  valid_release "$STATE_RELEASE" && [[ "${values[3]}" == "$STATE_RELEASE" && "$STATE_COLOR" =~ ^(blue|green)$ ]] || fail 'active state identity invalid'
  validate_manifest "$STATE_RELEASE"
  [[ "$MANIFEST_IMAGE" == "$STATE_IMAGE_ID" && "$MANIFEST_CHECKSUM" == "$STATE_MANIFEST_CHECKSUM" ]] || fail 'active state and manifest disagree'
  expected="$(printf 'header X-AI-ERP-Release "%s"\nreverse_proxy app-%s:8080' "$STATE_RELEASE" "$STATE_COLOR")"
  [[ "$(<"$UPSTREAM_FILE")" == "$expected" ]] || fail 'active upstream and state disagree'
  # Rollback may recover an absent or unhealthy active app, but any existing
  # container still has to agree with the verified ownership and image lineage.
  verify_container "app-$STATE_COLOR" "$STATE_IMAGE_ID" "$validation_mode"
  if [[ -n "$STATE_PREVIOUS_RELEASE" ]]; then
    valid_release "$STATE_PREVIOUS_RELEASE" && [[ "$STATE_PREVIOUS_RELEASE" != "$STATE_RELEASE" && "$STATE_PREVIOUS_COLOR" =~ ^(blue|green)$ && "$STATE_PREVIOUS_COLOR" != "$STATE_COLOR" ]] || fail 'previous release lineage invalid'
    validate_manifest "$STATE_PREVIOUS_RELEASE"
    [[ "$MANIFEST_CHECKSUM" == "${values[7]:-}" ]] || fail 'previous manifest differs from state lineage'
  else
    [[ -z "$STATE_PREVIOUS_COLOR" && -z "${values[7]:-}" ]] || fail 'empty previous release has inconsistent lineage'
  fi
}
wait_service_healthy() {
  local service="$1" attempt container status
  local attempts=36 interval=5
  if [[ "${AI_ERP_TEST_MODE:-0}" == 1 ]]; then attempts="${HEALTH_ATTEMPTS:-36}"; interval="${HEALTH_INTERVAL_SECONDS:-5}"; fi
  [[ "$attempts" =~ ^[1-9][0-9]*$ && "$interval" =~ ^[0-9]+$ ]] || fail 'invalid health retry bounds'
  for ((attempt=0; attempt<attempts; attempt++)); do
    container="$("${COMPOSE[@]}" ps -q "$service")"
    if [[ -n "$container" ]]; then
      status="$(docker inspect -f '{{.State.Health.Status}}' "$container")" || fail 'container health inspect failed'
      [[ "$status" == healthy ]] && return 0
    fi
    sleep "$interval"
  done
  fail 'service failed bounded health checks'
}
prepare_release() {
  RELEASE_DIR="$ROOT/releases/$RELEASE_ID"
  if [[ ! -e "$RELEASE_DIR" && ! -L "$RELEASE_DIR" ]]; then mkdir -- "$RELEASE_DIR"; fi
  secure_dir "$RELEASE_DIR"
  EVENT_FILE="$RELEASE_DIR/events.log"
  optional_file "$EVENT_FILE"
  touch "$EVENT_FILE"; chmod 600 "$EVENT_FILE"
  event started
}
event() { [[ -n "$EVENT_FILE" ]] || return 0; printf '%s %s\n' "$(now)" "$1" >>"$EVENT_FILE"; }
initialize_upstream() {
  if [[ ! -e "$UPSTREAM_FILE" ]]; then
    printf 'respond "service initializing" 503\n' >"$UPSTREAM_FILE"
    chmod 600 "$UPSTREAM_FILE"
  fi
}
cleanup_candidates() {
  local path
  for path in "$STATE_DIR/candidate.Caddyfile" "$STATE_DIR/upstream.candidate" "$STATE_DIR/state.candidate" "$STATE_DIR/upstream.previous" "$STATE_DIR/state.previous" "$STATE_DIR/upstream.restore" "$STATE_DIR/state.restore"; do
    optional_file "$path"
    rm -f -- "$path" || return 1
  done
  if [[ -n "$RELEASE_DIR" ]]; then
    for path in "$RELEASE_DIR/manifest.candidate" "$RELEASE_DIR/manifest.sha256.candidate"; do optional_file "$path"; rm -f -- "$path" || return 1; done
  fi
}
reload_caddy() { "${COMPOSE[@]}" exec -T caddy caddy reload --config /etc/caddy/source/Caddyfile --adapter caddyfile >/dev/null 2>&1; }
abort_transaction() {
  local recovery=0
  if [[ "$TRANSACTION" == 1 ]]; then
    if [[ "$SWITCH_ATTEMPTED" == 1 ]]; then
      if [[ "$HAD_UPSTREAM" == 1 ]]; then
        cp -- "$STATE_DIR/upstream.previous" "$STATE_DIR/upstream.restore" && mv -f -- "$STATE_DIR/upstream.restore" "$UPSTREAM_FILE" || recovery=1
      else
        printf 'respond "service initializing" 503\n' >"$STATE_DIR/upstream.restore" && mv -f -- "$STATE_DIR/upstream.restore" "$UPSTREAM_FILE" || recovery=1
      fi
      if [[ "$HAD_STATE" == 1 ]]; then
        cp -- "$STATE_DIR/state.previous" "$STATE_DIR/state.restore" && mv -f -- "$STATE_DIR/state.restore" "$STATE_FILE" || recovery=1
      else rm -f -- "$STATE_FILE" || recovery=1; fi
      reload_caddy || recovery=1
    fi
    if [[ "$NEW_MANIFEST" == 1 ]]; then rm -f -- "$RELEASE_DIR/manifest.json" || recovery=1; fi
    if [[ "$NEW_MANIFEST_CHECKSUM" == 1 ]]; then rm -f -- "$RELEASE_DIR/manifest.json.sha256" || recovery=1; fi
  fi
  # Restoring files does not confirm the running proxy accepted the old route.
  # Preserve a possibly live candidate and recovery evidence on any failure.
  if [[ "$recovery" == 0 && "$CANDIDATE_STARTED" == 1 ]]; then "${COMPOSE[@]}" stop "app-$CANDIDATE_COLOR" >/dev/null 2>&1 || recovery=1; fi
  if [[ "$recovery" == 0 ]]; then cleanup_candidates || recovery=1; fi
  if [[ "$recovery" != 0 ]]; then printf '%s\n' 'deployment recovery failed; preserve transaction files and inspect host' >&2; return 1; fi
}
on_error() {
  local code="$1"
  trap - ERR INT TERM
  # ERR propagates through command substitutions. Let only the entrypoint
  # process compensate so snapshots cannot be restored/deleted twice.
  if [[ "$BASHPID" != "$DEPLOY_MAIN_PID" ]]; then exit "$code"; fi
  set +e
  event failed
  if ! abort_transaction; then exit 2; fi
  [[ "$code" != 0 ]] || code=1
  exit "$code"
}
install_error_trap() { trap 'on_error "$?"' ERR; trap 'on_error 130' INT; trap 'on_error 143' TERM; }
transactional_promote() {
  local make_manifest="$1" observation=20
  HAD_UPSTREAM=0 HAD_STATE=0
  cleanup_candidates
  [[ ! -f "$UPSTREAM_FILE" ]] || { cp -- "$UPSTREAM_FILE" "$STATE_DIR/upstream.previous"; HAD_UPSTREAM=1; }
  [[ ! -f "$STATE_FILE" ]] || { cp -- "$STATE_FILE" "$STATE_DIR/state.previous"; HAD_STATE=1; }
  TRANSACTION=1
  printf 'header X-AI-ERP-Release "%s"\nreverse_proxy app-%s:8080\n' "$RELEASE_ID" "$CANDIDATE_COLOR" >"$STATE_DIR/upstream.candidate"
  # Validate the full exact configuration that the production import will load.
  python3 - "$PROJECT_DIR/infra/Caddyfile" "$STATE_DIR/upstream.candidate" >"$STATE_DIR/candidate.Caddyfile" <<'PY'
import sys
with open(sys.argv[1],encoding='utf-8') as f: config=f.read()
with open(sys.argv[2],encoding='utf-8') as f: upstream=f.read().rstrip()
needle='import /etc/caddy/state/active-upstream.caddy'
assert config.count(needle)==1
print(config.replace(needle,upstream))
PY
  "${COMPOSE[@]}" run --rm --no-deps caddy validate --config /etc/caddy/state/candidate.Caddyfile --adapter caddyfile >/dev/null 2>&1 || fail 'candidate Caddy config invalid'
  event validated
  SWITCH_ATTEMPTED=1
  mv -f -- "$STATE_DIR/upstream.candidate" "$UPSTREAM_FILE"
  reload_caddy || fail 'candidate Caddy reload failed'
  SWITCHED_AT="$(now)"; event switched
  "$(dirname -- "${BASH_SOURCE[0]}")/smoke.sh" public "$RELEASE_ID"
  if [[ "${AI_ERP_TEST_MODE:-0}" == 1 ]]; then observation="${OBSERVATION_SECONDS:-20}"; fi
  [[ "$observation" =~ ^[0-9]+$ ]] || fail 'invalid observation interval'
  event observing; sleep "$observation"
  "$(dirname -- "${BASH_SOURCE[0]}")/smoke.sh" public "$RELEASE_ID"
  if [[ "$make_manifest" == 1 ]]; then
    [[ ! -e "$RELEASE_DIR/manifest.json" && ! -L "$RELEASE_DIR/manifest.json" && ! -e "$RELEASE_DIR/manifest.json.sha256" ]] || fail 'completed manifest already exists'
    json_write releaseId "$RELEASE_ID" revision "$RELEASE_ID" imageId "$CANDIDATE_IMAGE" imageChecksum "$(text_checksum "$CANDIDATE_IMAGE")" color "$CANDIDATE_COLOR" previousRelease "$STATE_RELEASE" previousColor "$STATE_COLOR" startedAt "$STARTED_AT" migratedAt "$MIGRATED_AT" switchedAt "$SWITCHED_AT" completedAt "$(now)" composeChecksum "$(sha256 "$COMPOSE_FILE")" caddyChecksum "$(sha256 "$PROJECT_DIR/infra/Caddyfile")" migrationChecksum "$MIGRATION_CHECKSUM" migrationResult success >"$RELEASE_DIR/manifest.candidate"
    MANIFEST_CHECKSUM="$(sha256 "$RELEASE_DIR/manifest.candidate")"
    printf '%s\n' "$MANIFEST_CHECKSUM" >"$RELEASE_DIR/manifest.sha256.candidate"
    # Hard-link installation is atomic and fails if the immutable destination
    # already exists, even if another writer ignores the deployment lock.
    ln -- "$RELEASE_DIR/manifest.candidate" "$RELEASE_DIR/manifest.json"
    NEW_MANIFEST=1
    ln -- "$RELEASE_DIR/manifest.sha256.candidate" "$RELEASE_DIR/manifest.json.sha256"
    NEW_MANIFEST_CHECKSUM=1
  else validate_manifest "$RELEASE_ID"; fi
  json_write releaseId "$RELEASE_ID" color "$CANDIDATE_COLOR" imageId "$CANDIDATE_IMAGE" revision "$RELEASE_ID" previousRelease "$STATE_RELEASE" previousColor "$STATE_COLOR" manifestChecksum "$MANIFEST_CHECKSUM" previousManifestChecksum "$STATE_MANIFEST_CHECKSUM" updatedAt "$(now)" >"$STATE_DIR/state.candidate"
  mv -f -- "$STATE_DIR/state.candidate" "$STATE_FILE"
  secure_file "$STATE_FILE"
  event completed
  # From this point the complete release and traffic state are durable. Failure
  # to drain an old slot is reported, but cannot corrupt the committed state.
  TRANSACTION=0 SWITCH_ATTEMPTED=0 CANDIDATE_STARTED=0 NEW_MANIFEST=0 NEW_MANIFEST_CHECKSUM=0
  cleanup_candidates
  if [[ -n "$STATE_COLOR" ]]; then "${COMPOSE[@]}" stop "app-$STATE_COLOR" >/dev/null 2>&1 || fail 'release committed but former slot could not be stopped'; fi
}
