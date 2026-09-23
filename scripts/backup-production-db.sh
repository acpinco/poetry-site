#!/usr/bin/env bash

set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
config_file="${POETRY_BACKUP_CONFIG_FILE:-/etc/poetry-site-backup.env}"

[[ "${EUID}" -eq 0 ]] || { echo "Run this production backup helper with sudo." >&2; exit 1; }
[[ -f "$config_file" ]] || { echo "Missing backup configuration: $config_file" >&2; exit 1; }

# shellcheck source=/etc/poetry-site-backup.env
source "$config_file"

: "${BACKUP_OWNER:?BACKUP_OWNER is required}"
: "${BACKUP_DIRECTORY:?BACKUP_DIRECTORY is required}"
: "${RCLONE_REMOTE:?RCLONE_REMOTE is required}"
: "${RCLONE_CONFIG:?RCLONE_CONFIG is required}"
: "${RETENTION_DAYS:=31}"

[[ "$BACKUP_DIRECTORY" == /home/*/backups/* ]] || { echo "BACKUP_DIRECTORY must be a dedicated /home/.../backups/... path." >&2; exit 1; }
[[ "$RCLONE_REMOTE" == *:* && "${RCLONE_REMOTE#*:}" != "$RCLONE_REMOTE" ]] || { echo "RCLONE_REMOTE must name a remote and path, for example remote:poetry-site." >&2; exit 1; }
[[ "$RETENTION_DAYS" =~ ^[0-9]+$ && "$RETENTION_DAYS" -ge 1 ]] || { echo "RETENTION_DAYS must be a positive whole number." >&2; exit 1; }
id "$BACKUP_OWNER" >/dev/null

compose_file="$project_dir/compose.production.yaml"
[[ -f "$compose_file" ]] || { echo "Missing $compose_file" >&2; exit 1; }
[[ -f "$RCLONE_CONFIG" ]] || { echo "Missing rclone configuration: $RCLONE_CONFIG" >&2; exit 1; }

install -d -m 700 -o "$BACKUP_OWNER" -g "$BACKUP_OWNER" "$BACKUP_DIRECTORY"

timestamp="$(date -u +%Y-%m-%dT%H%M%SZ)"
archive_name="poetry-site-${timestamp}.dump"
archive_path="$BACKUP_DIRECTORY/$archive_name"
checksum_path="$archive_path.sha256"
temporary_path="$BACKUP_DIRECTORY/.${archive_name}.partial"
temporary_checksum="$temporary_path.sha256"

cleanup() { rm -f "$temporary_path" "$temporary_checksum"; }
trap cleanup EXIT

echo "[$(date -u --iso-8601=seconds)] Creating PostgreSQL archive $archive_name"
docker compose -f "$compose_file" exec -T postgres sh -c \
  'pg_dump --format=custom --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$temporary_path"
[[ -s "$temporary_path" ]] || { echo "pg_dump produced an empty archive." >&2; exit 1; }

echo "[$(date -u --iso-8601=seconds)] Validating PostgreSQL archive"
docker compose -f "$compose_file" exec -T postgres sh -c 'pg_restore --list' < "$temporary_path" > /dev/null
sha256sum "$temporary_path" > "$temporary_checksum"
mv "$temporary_path" "$archive_path"
mv "$temporary_checksum" "$checksum_path"
chown "$BACKUP_OWNER:$BACKUP_OWNER" "$archive_path" "$checksum_path"

run_rclone() {
  runuser -u "$BACKUP_OWNER" -- env RCLONE_CONFIG="$RCLONE_CONFIG" rclone "$@"
}

echo "[$(date -u --iso-8601=seconds)] Uploading encrypted archive to $RCLONE_REMOTE"
run_rclone copyto "$archive_path" "$RCLONE_REMOTE/$archive_name"
run_rclone copyto "$checksum_path" "$RCLONE_REMOTE/$archive_name.sha256"
run_rclone lsf "$RCLONE_REMOTE/$archive_name" > /dev/null
run_rclone lsf "$RCLONE_REMOTE/$archive_name.sha256" > /dev/null

echo "[$(date -u --iso-8601=seconds)] Pruning backups older than $RETENTION_DAYS days"
find "$BACKUP_DIRECTORY" -maxdepth 1 -type f \( -name 'poetry-site-*.dump' -o -name 'poetry-site-*.dump.sha256' \) -mtime "+$RETENTION_DAYS" -delete
run_rclone delete "$RCLONE_REMOTE" --min-age "${RETENTION_DAYS}d" --include 'poetry-site-*.dump' --include 'poetry-site-*.dump.sha256'

echo "[$(date -u --iso-8601=seconds)] Backup completed and verified: $archive_name"
