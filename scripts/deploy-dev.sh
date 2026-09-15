#!/usr/bin/env bash

set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

if [[ ! -f .env ]]; then
  echo "Missing .env. Copy .env.example and set a local PostgreSQL password first." >&2
  exit 1
fi

docker compose config --quiet
docker compose up --detach --build

for attempt in {1..30}; do
  if curl --fail --silent --show-error --max-time 2 http://127.0.0.1:8080/swagger-ui.html > /dev/null; then
    docker compose ps
    printf '\nLocal site:  http://localhost:5173\nSwagger:     http://localhost:8080/swagger-ui.html\nMailpit:     http://localhost:8025\n'
    exit 0
  fi
  sleep 2
done

echo "The local Spring application did not become ready within 60 seconds." >&2
docker compose logs --tail=100 app >&2
exit 1
