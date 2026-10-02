#!/usr/bin/env bash

set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
config_file="${POETRY_BACKUP_CONFIG_FILE:-$project_dir/.env.backup}"

[[ "${EUID}" -eq 0 ]] || { echo "Run this production backup helper with sudo." >&2; exit 1; }
[[ -f "$config_file" ]] || { echo "Missing backup configuration: $config_file (copy .env.backup.example)" >&2; exit 1; }

# shellcheck source=../.env.backup.example
source "$config_file"

: "${BACKUP_OWNER:?BACKUP_OWNER is required}"
: "${BACKUP_DIRECTORY:?BACKUP_DIRECTORY is required}"
: "${RETENTION_DAYS:=31}"
: "${USB_BACKUP_MOUNT:=}"

[[ "$BACKUP_DIRECTORY" == /home/*/backups/* ]] || { echo "BACKUP_DIRECTORY must be a dedicated /home/.../backups/... path." >&2; exit 1; }
[[ "$RETENTION_DAYS" =~ ^[0-9]+$ && "$RETENTION_DAYS" -ge 1 ]] || { echo "RETENTION_DAYS must be a positive whole number." >&2; exit 1; }
[[ -z "$USB_BACKUP_MOUNT" || "$USB_BACKUP_MOUNT" == /mnt/* ]] || { echo "USB_BACKUP_MOUNT must be a /mnt/... mount point." >&2; exit 1; }
id "$BACKUP_OWNER" >/dev/null

compose_file="$project_dir/compose.production.yaml"
[[ -f "$compose_file" ]] || { echo "Missing $compose_file" >&2; exit 1; }

install -d -m 700 -o "$BACKUP_OWNER" -g "$BACKUP_OWNER" "$BACKUP_DIRECTORY"

timestamp="$(date -u +%Y-%m-%dT%H%M%SZ)"
archive_name="poetry-site-${timestamp}.dump"
archive_path="$BACKUP_DIRECTORY/$archive_name"
checksum_path="$archive_path.sha256"
temporary_path="$BACKUP_DIRECTORY/.${archive_name}.partial"
temporary_checksum="$temporary_path.sha256"

usb_partial=""
cleanup() { rm -f "$temporary_path" "$temporary_checksum" ${usb_partial:+"$usb_partial"}; }
trap cleanup EXIT

echo "[$(date -u --iso-8601=seconds)] Creating PostgreSQL archive $archive_name"
docker compose -f "$compose_file" exec -T postgres sh -c \
  'pg_dump --format=custom --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$temporary_path"
[[ -s "$temporary_path" ]] || { echo "pg_dump produced an empty archive." >&2; exit 1; }

echo "[$(date -u --iso-8601=seconds)] Validating PostgreSQL archive"
docker compose -f "$compose_file" exec -T postgres sh -c 'pg_restore --list' < "$temporary_path" > /dev/null
mv "$temporary_path" "$archive_path"
(
  cd "$BACKUP_DIRECTORY"
  sha256sum "$archive_name" > "$temporary_checksum"
)
mv "$temporary_checksum" "$checksum_path"
chown "$BACKUP_OWNER:$BACKUP_OWNER" "$archive_path" "$checksum_path"

echo "[$(date -u --iso-8601=seconds)] Pruning backups older than $RETENTION_DAYS days"
find "$BACKUP_DIRECTORY" -maxdepth 1 -type f \( -name 'poetry-site-*.dump' -o -name 'poetry-site-*.dump.sha256' \) -mtime "+$RETENTION_DAYS" -delete

echo "[$(date -u --iso-8601=seconds)] Local backup completed and verified: $archive_name"

[[ -n "$USB_BACKUP_MOUNT" ]] || exit 0

# An unplugged stick leaves an empty mount-point directory on the laptop's own SSD.
# Writing there would look like a second copy while protecting nothing, so refuse.
if ! mountpoint -q "$USB_BACKUP_MOUNT"; then
  echo "[$(date -u --iso-8601=seconds)] USB COPY FAILED: no drive is mounted at $USB_BACKUP_MOUNT. The SSD backup above is intact." >&2
  exit 1
fi

usb_directory="$USB_BACKUP_MOUNT/poetry-site"
usb_partial="$usb_directory/.${archive_name}.partial"
echo "[$(date -u --iso-8601=seconds)] Copying archive to USB drive at $usb_directory"
mkdir -p "$usb_directory"
cp "$archive_path" "$usb_partial"
# sync with a file argument flushes it to the stick and reports write errors.
sync "$usb_partial"
expected_checksum="$(cut -d ' ' -f 1 "$checksum_path")"
actual_checksum="$(sha256sum "$usb_partial" | cut -d ' ' -f 1)"
[[ "$actual_checksum" == "$expected_checksum" ]] || { echo "USB COPY FAILED: checksum mismatch for $archive_name." >&2; exit 1; }
mv "$usb_partial" "$usb_directory/$archive_name"
usb_partial=""
cp "$checksum_path" "$usb_directory/$archive_name.sha256"
sync "$usb_directory/$archive_name.sha256"
# FAT and exFAT sticks have no Unix owners (the mount options set them); ext4 does.
chown "$BACKUP_OWNER:$BACKUP_OWNER" "$usb_directory" "$usb_directory/$archive_name" "$usb_directory/$archive_name.sha256" 2>/dev/null || true

echo "[$(date -u --iso-8601=seconds)] Pruning USB backups older than $RETENTION_DAYS days"
find "$usb_directory" -maxdepth 1 -type f \( -name 'poetry-site-*.dump' -o -name 'poetry-site-*.dump.sha256' \) -mtime "+$RETENTION_DAYS" -delete

echo "[$(date -u --iso-8601=seconds)] USB copy completed and verified: $usb_directory/$archive_name"
