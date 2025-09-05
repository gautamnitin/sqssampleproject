#!/usr/bin/env bash
set -euo pipefail

# This script runs inside LocalStack container (ready.d hook).
# It will create the SQS queue used by the application.

QUEUE_NAME="employee-queue"

if command -v awslocal >/dev/null 2>&1; then
  echo "[init] Creating SQS queue: ${QUEUE_NAME}"
  awslocal sqs create-queue --queue-name "${QUEUE_NAME}" >/dev/null
  echo "[init] Done."
else
  echo "awslocal not found; attempting with aws cli"
  aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name "${QUEUE_NAME}" || true
fi
