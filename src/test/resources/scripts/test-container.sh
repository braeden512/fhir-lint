#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAG="${1:-fhir-lint:test}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker command not found; skipping container tests."
  exit 0
fi

echo "=== 1. Testing Container Version & Help ==="
docker run --rm "$IMAGE_TAG" --version
docker run --rm "$IMAGE_TAG" --help

echo "=== 2. Testing Volume-Mounted Clean Bundle (Exit 0) ==="
docker run --rm \
  -v "$(pwd)/sample-data:/workspace/sample-data:ro" \
  "$IMAGE_TAG" validate /workspace/sample-data/clean/clean-bundle.json

echo "=== 3. Testing Quality Gate Threshold Failure (Exit 1) ==="
set +e
docker run --rm \
  -v "$(pwd)/sample-data:/workspace/sample-data:ro" \
  "$IMAGE_TAG" validate /workspace/sample-data/clean/clean-bundle.json --min-score 99
EXIT_CODE=$?
set -e

if [ "$EXIT_CODE" -ne 1 ]; then
  echo "Expected exit code 1 for gate failure, but got: $EXIT_CODE"
  exit 1
fi
echo "Quality gate exit code 1 verified."

echo "=== 4. Testing Piped Standard Input Streaming ==="
cat sample-data/clean/clean-bundle.json | docker run -i --rm "$IMAGE_TAG" validate - --format json | grep -q '"overallScore"'
echo "Piped standard input verified."

echo "=== All Container Tests Passed ==="
