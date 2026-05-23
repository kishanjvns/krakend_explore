#!/usr/bin/env bash
# Usage: ./build.sh [tag]   — default tag is "latest"
set -euo pipefail

TAG="${1:-latest}"
LOG_DIR="$(mktemp -d)"
START=$(date +%s)

SERVICES=(
  "user-service"
  "doctor-service"
  "appointment-service"
  "payment-service"
  "notification-service"
  "emr-service"
  "analytics-service"
  "frontend"
)

IMAGE_NAMES=(
  "mediq/user-service"
  "mediq/doctor-service"
  "mediq/appointment-service"
  "mediq/payment-service"
  "mediq/notification-service"
  "mediq/emr-service"
  "mediq/analytics-service"
  "mediq/frontend"
)

PIDS=()

echo "Building ${#SERVICES[@]} images in parallel  (tag: $TAG)"
echo "Logs: $LOG_DIR"
echo "────────────────────────────────────────"

for i in "${!SERVICES[@]}"; do
  svc="${SERVICES[$i]}"
  img="${IMAGE_NAMES[$i]}:$TAG"
  docker build -t "$img" "./$svc" > "$LOG_DIR/$svc.log" 2>&1 &
  PIDS+=($!)
  printf "  ▶ %-30s  (pid %d)\n" "$img" "${PIDS[$i]}"
done

echo "────────────────────────────────────────"
echo "Waiting for builds to finish..."
echo ""

FAILED=0
for i in "${!PIDS[@]}"; do
  svc="${SERVICES[$i]}"
  img="${IMAGE_NAMES[$i]}:$TAG"
  if wait "${PIDS[$i]}"; then
    printf "  ✓ %s\n" "$img"
  else
    printf "  ✗ %s  —  see %s/%s.log\n" "$img" "$LOG_DIR" "$svc"
    FAILED=$((FAILED + 1))
  fi
done

END=$(date +%s)
echo ""
echo "────────────────────────────────────────"
ELAPSED=$((END - START))
if [ "$FAILED" -eq 0 ]; then
  echo "All ${#SERVICES[@]} builds succeeded  (${ELAPSED}s)"
else
  echo "$FAILED / ${#SERVICES[@]} builds FAILED  (${ELAPSED}s)"
  echo "Run:  cat $LOG_DIR/<service>.log  to inspect failures"
  exit 1
fi
