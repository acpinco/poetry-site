#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  echo "Usage:" >&2
  echo "  $0 dry-run [limit]" >&2
  echo "  $0 test [limit] [--resend]" >&2
  echo "  $0 live SEND_LEGACY_POETRY_TO_HISTORICAL_EMAILS [limit] [--resend]" >&2
  exit 2
}

[[ $# -ge 1 ]] || usage

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
[[ -f .env.production ]] || { echo "Missing .env.production." >&2; exit 2; }

mode="$1"
confirmation=""
resend=false
case "$mode" in
  dry-run)
    limit="${2:-500}"
    ;;
  test)
    # One personalized candidate goes only to the configured test inboxes.
    limit="${2:-1}"
    [[ "${3:-}" == "--resend" ]] && resend=true
    ;;
  live)
    [[ $# -ge 2 && "$2" == "SEND_LEGACY_POETRY_TO_HISTORICAL_EMAILS" ]] || usage
    confirmation="$2"
    # Send in small, resumable batches. Previously successful deliveries are skipped.
    limit="${3:-25}"
    [[ "${4:-}" == "--resend" ]] && resend=true
    ;;
  *) usage ;;
esac

sudo docker compose -f compose.production.yaml exec -T app \
  java -jar /app/app.jar \
  --spring.main.web-application-type=none \
  --app.legacy-outreach.enabled=true \
  --app.legacy-outreach.mode="$mode" \
  --app.legacy-outreach.limit="$limit" \
  --app.legacy-outreach.confirmation="$confirmation" \
  --app.legacy-outreach.resend="$resend"
