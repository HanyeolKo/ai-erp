#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=backup
source "$(dirname -- "$0")/lib-deploy.sh"
RELEASE_ID="${1:-}"
[[ $# == 1 ]] && valid_release "$RELEASE_ID" || fail 'releaseId must be an exact lowercase 40-character commit SHA'
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
[[ "$RETENTION_DAYS" =~ ^[0-9]{1,4}$ ]] || fail 'BACKUP_RETENTION_DAYS must be a non-negative integer'
validate_base
acquire_lock
verify_no_transaction
load_env
export APP_IMAGE="ai-erp:$RELEASE_ID"
verify_resources
verify_migrations
prepare_release
BACKUP_DIR="$RELEASE_DIR/backups"
if [[ ! -e "$BACKUP_DIR" && ! -L "$BACKUP_DIR" ]]; then mkdir -- "$BACKUP_DIR"; fi
secure_dir "$BACKUP_DIR"
probe="$("${COMPOSE[@]}" exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "select to_regclass('platform.flyway_schema_history') is not null" 2>/dev/null)" || fail 'database history probe failed'
[[ "$probe" == t || "$probe" == f ]] || fail 'database history probe returned an invalid value'
stamp="$(date -u +%Y%m%dT%H%M%SZ)-$$"
dump="$BACKUP_DIR/postgres-$stamp.dump"; metadata="$BACKUP_DIR/postgres-$stamp.json"
for path in "$dump" "$metadata" "$dump.candidate" "$metadata.candidate"; do [[ ! -e "$path" && ! -L "$path" ]] || fail 'backup target already exists'; done
backup_failed() { trap - ERR INT TERM; rm -f -- "$dump.candidate" "$metadata.candidate" "$dump" "$metadata"; exit 1; }
trap backup_failed ERR INT TERM
"${COMPOSE[@]}" exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc >"$dump.candidate" 2>/dev/null || fail 'database dump failed'
[[ -s "$dump.candidate" ]] || fail 'database dump is empty'
"${COMPOSE[@]}" exec -T postgres pg_restore -l <"$dump.candidate" >/dev/null 2>&1 || fail 'database dump failed validation'
state_checksum=""; if [[ -f "$STATE_FILE" ]]; then state_checksum="$(sha256 "$STATE_FILE")"; fi
json_write releaseId "$RELEASE_ID" createdAt "$(now)" format custom dumpChecksum "$(sha256 "$dump.candidate")" composeChecksum "$(sha256 "$COMPOSE_FILE")" caddyChecksum "$(sha256 "$PROJECT_DIR/infra/Caddyfile")" migrationChecksum "$(migration_checksum)" historyExisted "$probe" activeStateChecksum "$state_checksum" >"$metadata.candidate"
mv -f -- "$dump.candidate" "$dump"
mv -f -- "$metadata.candidate" "$metadata"
secure_file "$dump"; secure_file "$metadata"
trap - ERR INT TERM
# Retention only recognizes our exact dedicated dump/metadata filenames; never
# traverse symlinks or remove directories, volumes, images, or other artifacts.
while IFS= read -r -d '' path; do
  [[ "${path##*/}" =~ ^postgres-[0-9]{8}T[0-9]{6}Z-[0-9]+\.(dump|json)$ ]] || continue
  secure_file "$path"
  rm -f -- "$path"
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -mtime "+$RETENTION_DAYS" -print0)
event backed-up
printf 'backup: ok release=%s history=%s\n' "$RELEASE_ID" "$probe"
