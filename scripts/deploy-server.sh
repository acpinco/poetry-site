#!/usr/bin/env bash

set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

if [[ ! -f .env.production ]]; then
  echo "Missing .env.production. Copy .env.production.example and set real secret values first." >&2
  exit 1
fi

sudo docker compose -f compose.production.yaml config --quiet
sudo docker compose -f compose.production.yaml up --detach --build
sudo docker compose -f compose.production.yaml ps
