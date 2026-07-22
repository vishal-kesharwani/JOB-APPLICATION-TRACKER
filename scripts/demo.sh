#!/usr/bin/env bash
# End-to-end demo: drive an application through its lifecycle and watch the
# events ripple to the notification and analytics services.
#
# Run this in a SEPARATE terminal while `docker compose ... up` keeps running.
#
# Usage: ./scripts/demo.sh            (defaults to localhost ports)
#        APP=http://localhost:8081 NOTIF=http://localhost:8082 ANALYTICS=http://localhost:8083 ./scripts/demo.sh
set -uo pipefail

APP="${APP:-http://localhost:8081}"
NOTIF="${NOTIF:-http://localhost:8082}"
ANALYTICS="${ANALYTICS:-http://localhost:8083}"

say()  { printf "\n\033[1;36m== %s ==\033[0m\n" "$1"; }
fail() { printf "\n\033[1;31m%s\033[0m\n" "$1" >&2; exit 1; }

# --- Preflight: make sure the services are reachable before we start ---------
wait_for() {
  local name="$1" url="$2" tries=120   # ~4 min; JVM cold start can take 3 min
  printf "Waiting for %s (%s) " "$name" "$url"
  until curl -sf -o /dev/null "$url/actuator/health"; do
    tries=$((tries - 1))
    if [ "$tries" -le 0 ]; then
      echo
      fail "$name is not responding at $url.
Is the stack running? Check with:  docker compose ps
If it is still building/starting, wait a minute and re-run this script."
    fi
    printf "."
    sleep 2
  done
  printf " ready\n"
}

wait_for "application-service" "$APP"
wait_for "notification-service" "$NOTIF"
wait_for "analytics-service"    "$ANALYTICS"

# --- Demo --------------------------------------------------------------------
say "Create three applications"
create() {
  curl -sS -X POST "$APP/api/v1/applications" -H "Content-Type: application/json" \
    -d "{\"company\":\"$1\",\"role\":\"$2\",\"appliedDate\":\"2026-07-12\",\"resumeVersion\":\"$3\"}"
  echo
}
RESP1=$(create "Atidan Technologies" "SDE" "java-aiml")
echo "$RESP1"
ID1=$(printf '%s' "$RESP1" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
create "Netflix" "Backend Engineer" "java-distributed"
create "Stripe"  "Platform Engineer" "java-distributed"

if [ -z "$ID1" ]; then
  fail "Could not parse the created application id from the response above."
fi

say "Move application $ID1 to INTERVIEW_SCHEDULED (triggers interview-prep reminder)"
curl -sS -X PATCH "$APP/api/v1/applications/$ID1/status" \
  -H "Content-Type: application/json" -d '{"status":"INTERVIEW_SCHEDULED"}'; echo

say "Move application $ID1 to OFFER_RECEIVED"
curl -sS -X PATCH "$APP/api/v1/applications/$ID1/status" \
  -H "Content-Type: application/json" -d '{"status":"OFFER_RECEIVED"}'; echo

sleep 2

say "Notification service — reminder summary"
curl -sS "$NOTIF/api/v1/reminders/summary"; echo

say "Analytics service — pipeline summary (from Kafka -> Redis)"
curl -sS "$ANALYTICS/api/v1/analytics/summary"; echo
say "Analytics — by company"
curl -sS "$ANALYTICS/api/v1/analytics/by-company"; echo
say "Analytics — conversion funnel"
curl -sS "$ANALYTICS/api/v1/analytics/funnel"; echo

say "Done. Open Grafana at http://localhost:3000 (admin/admin) for metrics & traces."
