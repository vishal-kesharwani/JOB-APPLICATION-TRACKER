#!/usr/bin/env bash
# Chaos test: kill a running application-service pod and prove Kubernetes
# self-heals (a replacement pod is scheduled and passes readiness).
#
# Usage: ./scripts/chaos.sh
set -euo pipefail

NS="jobtracker"
APP_LABEL="app=application-service"

echo "== Pods before =="
kubectl -n "$NS" get pods -l "$APP_LABEL" -o wide

VICTIM=$(kubectl -n "$NS" get pods -l "$APP_LABEL" -o jsonpath='{.items[0].metadata.name}')
echo -e "\n== Deleting pod: $VICTIM =="
kubectl -n "$NS" delete pod "$VICTIM"

echo -e "\n== Watching recovery (Ctrl-C once all pods are Running/Ready) =="
kubectl -n "$NS" get pods -l "$APP_LABEL" -w
