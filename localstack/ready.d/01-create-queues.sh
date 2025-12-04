#!/usr/bin/env bash
set -euo pipefail

# This script runs inside LocalStack container (ready.d hook).
# It will create the SQS queues used by the application.

QUEUE_NAME="employee-queue"
DLQ_NAME="employee-queue-dlq"

if command -v awslocal >/dev/null 2>&1; then
  # Create DLQ first
  echo "[init] Creating SQS DLQ: ${DLQ_NAME}"
  DLQ_URL=$(awslocal sqs create-queue --queue-name "${DLQ_NAME}" --query 'QueueUrl' --output text)
  DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url "${DLQ_URL}" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
  echo "[init] Created DLQ: ${DLQ_ARN}"
  
  # Create main queue with redrive policy pointing to DLQ
  echo "[init] Creating SQS queue: ${QUEUE_NAME} with redrive policy to DLQ"
  REDRIVE_POLICY="{\"deadLetterTargetArn\":\"${DLQ_ARN}\",\"maxReceiveCount\":3}"
  awslocal sqs create-queue \
    --queue-name "${QUEUE_NAME}" \
    --attributes "{\"RedrivePolicy\":\"${REDRIVE_POLICY}\"}" >/dev/null
  
  echo "[init] Done."
else
  echo "awslocal not found; attempting with aws cli"
  # Create DLQ
  aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name "${DLQ_NAME}" || true
  DLQ_URL=$(aws --endpoint-url=http://localhost:4566 sqs get-queue-url --queue-name "${DLQ_NAME}" --query 'QueueUrl' --output text)
  DLQ_ARN=$(aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes --queue-url "${DLQ_URL}" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
  
  # Create main queue with redrive policy
  REDRIVE_POLICY="{\"deadLetterTargetArn\":\"${DLQ_ARN}\",\"maxReceiveCount\":3}"
  aws --endpoint-url=http://localhost:4566 sqs create-queue \
    --queue-name "${QUEUE_NAME}" \
    --attributes "{\"RedrivePolicy\":\"${REDRIVE_POLICY}\"}" || true
fi
