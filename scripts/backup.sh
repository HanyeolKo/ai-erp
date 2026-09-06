#!/usr/bin/env bash
set -euo pipefail

ROOT="${AI_ERP_ROOT:-/home/deploy/ai-erp}"
PROJECT_DIR="${AI_ERP_PROJECT_DIR:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)}"
RELEASE_ID="${1:-}"
ENV_FILE="$ROOT/shared/.env"
COMPOSE=(docker compose --project-name ai-erp-phase1 --env-file "$ENV_FILE" -f "$PROJECT_DIR/infra/compose.prod.yml")
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

fail() { printf 'backup: %s\n' "$*" >&2; exit 1; }
[[ "$RELEASE_ID" =~ ^[a-f0-9]{40}$ ]] || fail 'releaseId must be an exact lowercase 40-character commit SHA'
RELEASE_DIR="$ROOT/releases/$RELEASE_ID"
BACKUP_DIR="$RELEASE_DIR/backups"
[[ "$BACKUP_DIR" == "$ROOT/releases/$RELEASE_ID/backups" ]] || fail 'unsafe backup path'
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump="$BACKUP_DIR/postgres-$timestamp.dump"
${COMPOSE[@]} exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc >"$dump"
chmod 600 "$dump"
metadata="$BACKUP_DIR/metadata-$timestamp.txt"
{
  printf 'releaseId=%s\n' "$RELEASE_ID"
  printf 'createdAt=%s\n' "$timestamp"
  printf 'composeSha256=%s\n' "$(sha256sum "$PROJECT_DIR/infra/compose.prod.yml" | awk '{print $1}')"
} >"$metadata"
chmod 600 "$metadata"
find "$BACKUP_DIR" -xdev -type f -mtime "+$RETENTION_DAYS" -delete
printf 'backup: ok release=%s\n' "$RELEASE_ID"

