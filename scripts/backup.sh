#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
SCRIPT_NAME=backup
source "$(dirname -- "$0")/lib-deploy.sh"
RELEASE_ID="${1:-}"; RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
valid_release "$RELEASE_ID" || fail 'releaseId must be an exact lowercase 40-character commit SHA'
[[ "$RETENTION_DAYS" =~ ^[0-9]{1,4}$ ]] || fail 'BACKUP_RETENTION_DAYS must be a non-negative integer'
load_env; validate_environment
BACKUP_DIR="$ROOT/releases/$RELEASE_ID/backups"; [[ "$BACKUP_DIR" == "$ROOT/releases/$RELEASE_ID/backups" ]] || fail 'unsafe backup path'
mkdir -p "$BACKUP_DIR"; chmod 700 "$BACKUP_DIR"
probe=""
if ! probe="$("${COMPOSE[@]}" exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "select to_regclass('platform.flyway_schema_history') is not null")"; then fail 'database history probe failed'; fi
case "$probe" in t) :;; f) printf 'backup: skipped empty database\n'; exit 0;; *) fail 'database history probe returned an invalid value';; esac
tmp="$BACKUP_DIR/.postgres-$(date -u +%Y%m%dT%H%M%SZ).tmp"; final="${tmp%.tmp}.dump"
"${COMPOSE[@]}" exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc >"$tmp"
[[ -s "$tmp" ]] || fail 'database dump is empty'
"${COMPOSE[@]}" exec -T postgres pg_restore -l <"$tmp" >/dev/null || fail 'database dump is invalid'
mv -f "$tmp" "$final"; chmod 600 "$final"
find "$BACKUP_DIR" -xdev -type f -name '*.dump' -mtime "+$RETENTION_DAYS" -delete
printf 'backup: ok release=%s\n' "$RELEASE_ID"
