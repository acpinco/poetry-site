#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 3 || ( "$1" != "dry-run" && "$1" != "apply" ) ]]; then
  echo "Usage: $0 <dry-run|apply> <users.csv> <poems.csv>" >&2
  exit 2
fi

mode="$1"
users_file="$(realpath "$2")"
poems_file="$(realpath "$3")"
[[ -f "$users_file" && -f "$poems_file" ]] || { echo "CSV file not found" >&2; exit 2; }

./mvnw -q -DskipTests package
apply=false
[[ "$mode" == "apply" ]] && apply=true
java -jar target/think-or-drink-poetry-0.0.1-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --app.legacy-import.enabled=true \
  --app.legacy-import.apply="$apply" \
  --app.legacy-import.users-file="$users_file" \
  --app.legacy-import.poems-file="$poems_file"
