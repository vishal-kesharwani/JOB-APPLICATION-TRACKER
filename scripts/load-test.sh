#!/usr/bin/env bash
# Generate sustained write load to exercise the HPA on application-service.
# Watch it scale with:  kubectl -n jobtracker get hpa -w
#
# Usage: ./scripts/load-test.sh [requests] [concurrency]
set -euo pipefail

APP="${APP:-http://localhost:8081}"
REQUESTS="${1:-2000}"
CONCURRENCY="${2:-20}"

body='{"company":"LoadCo","role":"SDE","appliedDate":"2026-07-12","resumeVersion":"bench"}'

echo "Firing $REQUESTS requests at $APP with concurrency $CONCURRENCY ..."
seq "$REQUESTS" | xargs -P "$CONCURRENCY" -I{} \
  curl -s -o /dev/null -X POST "$APP/api/v1/applications" \
    -H "Content-Type: application/json" -d "$body"
echo "Load complete."
