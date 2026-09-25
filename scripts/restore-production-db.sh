#!/usr/bin/env bash

set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
confirmation_flag="--confirm-replace-live-database"

usage() {
  cat <<EOF
Usage: sudo $0 $confirmation_flag /absolute/path/to/poetry-site-YYYY-MM-DDTHHMMSSZ.dump

This permanently replaces the live production database with the selected backup.
The application is stopped during the restore and restarted afterwards.
EOF
}

[[ "${1:-}" == "--help" ]] && {
  usage
  exit 0
}
[[ "${EUID}" -eq 0 ]] || {
  echo "Run this production restore helper with sudo." >&2
  exit 1
}
[[ "$#" -eq 2 && "$1" == "$confirmation_flag" ]] || {
  usage >&2
  exit 2
}

archive_path="$2"
[[ "$archive_path" = /* && "$archive_path" == *.dump && -f "$archive_path" ]] || {
  echo "Provide an existing absolute .dump archive path." >&2
  exit 2
}

compose_file="$project_dir/compose.production.yaml"
[[ -f "$compose_file" ]] || {
  echo "Missing $compose_file" >&2
  exit 1
}

checksum_path="$archive_path.sha256"
if [[ -f "$checksum_path" ]]; then
  echo "[$(date -u --iso-8601=seconds)] Verifying archive checksum"
  (
    cd "$(dirname "$archive_path")"
    sha256sum --check "$(basename "$checksum_path")"
  )
fi

echo "[$(date -u --iso-8601=seconds)] Validating PostgreSQL archive"
docker compose -f "$compose_file" exec -T postgres sh -c 'pg_restore --list' < "$archive_path" > /dev/null

app_was_stopped=false
restart_application() {
  if [[ "$app_was_stopped" == true ]]; then
    docker compose -f "$compose_file" up --detach app
  fi
}
trap restart_application EXIT

if docker compose -f "$compose_file" ps --status running --quiet app | grep -q .; then
  echo "[$(date -u --iso-8601=seconds)] Stopping the application before restore"
  docker compose -f "$compose_file" stop app
  app_was_stopped=true
fi

echo "[$(date -u --iso-8601=seconds)] Restoring PostgreSQL archive: $(basename "$archive_path")"
docker compose -f "$compose_file" exec -T postgres sh -c \
  'pg_restore --clean --if-exists --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$archive_path"

echo "[$(date -u --iso-8601=seconds)] Production database restore completed."
