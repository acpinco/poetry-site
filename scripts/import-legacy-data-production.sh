#!/usr/bin/env bash

set -Eeuo pipefail

# Disabled after the one-time production legacy import. Production data is now
# live and must never be cleared or re-imported through this helper.
echo "Production legacy import is permanently disabled. Do not clear or re-import the production database." >&2
exit 1

# Historical implementation retained below only as a record of the completed import.
if [[ $# -ne 3 || ( "$1" != "dry-run" && "$1" != "apply" ) ]]; then
  echo "Usage: $0 <dry-run|apply> <users.csv> <poems.csv>" >&2
  exit 2
fi

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

[[ -f .env.production ]] || { echo "Missing .env.production." >&2; exit 2; }
users_file="$(realpath "$2")"
poems_file="$(realpath "$3")"
[[ -f "$users_file" && -f "$poems_file" ]] || { echo "CSV file not found." >&2; exit 2; }

container_id="$(sudo docker compose -f compose.production.yaml ps -q app)"
[[ -n "$container_id" ]] || { echo "Production app container is not running. Run scripts/deploy-server.sh first." >&2; exit 2; }

container_users="/tmp/legacy-users-$$.csv"
container_poems="/tmp/legacy-poems-$$.csv"
cleanup() { sudo docker exec "$container_id" rm -f "$container_users" "$container_poems" >/dev/null 2>&1 || true; }
trap cleanup EXIT

sudo docker cp "$users_file" "$container_id:$container_users"
sudo docker cp "$poems_file" "$container_id:$container_poems"

apply=false
[[ "$1" == "apply" ]] && apply=true
sudo docker compose -f compose.production.yaml exec -T app \
  java -jar /app/app.jar \
  --spring.main.web-application-type=none \
  --app.legacy-import.enabled=true \
  --app.legacy-import.apply="$apply" \
  --app.legacy-import.users-file="$container_users" \
  --app.legacy-import.poems-file="$container_poems"
