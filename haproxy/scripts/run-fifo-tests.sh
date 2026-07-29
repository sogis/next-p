#!/usr/bin/env bash
set -euo pipefail

# Owns the local/CI lifecycle for the complete FIFO experiment. Artifacts are
# never deleted; a failing test also retains the Compose stack for inspection.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

compose=(docker compose)
if [[ -n "${COMPOSE_FILE:-}" ]]; then
  compose+=(--file "$COMPOSE_FILE")
fi

artifact_root="${FIFO_ARTIFACTS_DIR:-artifacts}"
gradle_command="${FIFO_GRADLE_COMMAND:-./gradlew}"
retain_stack="${FIFO_RETAIN_STACK:-false}"

case "$retain_stack" in
  1|true|TRUE|yes|YES) retain_after_success=true ;;
  0|false|FALSE|no|NO|"") retain_after_success=false ;;
  *)
    echo "FIFO_RETAIN_STACK must be true or false, received: $retain_stack" >&2
    exit 64
    ;;
esac

cleanup() {
  exit_code=$?
  trap - EXIT
  if [[ "$exit_code" -ne 0 || "$retain_after_success" == true ]]; then
    echo "Compose stack retained; artifacts are under $artifact_root" >&2
    exit "$exit_code"
  fi

  echo "Stopping Compose stack after successful FIFO test run" >&2
  "${compose[@]}" down
}

if ! command -v "$gradle_command" >/dev/null 2>&1; then
  echo "Gradle executable not found: $gradle_command" >&2
  echo "Use the included ./gradlew wrapper, or set FIFO_GRADLE_COMMAND." >&2
  exit 127
fi

trap cleanup EXIT

mkdir -p "$artifact_root"
echo "Starting Compose stack and waiting for service health checks" >&2
"${compose[@]}" up --detach --wait --force-recreate

echo "Running FIFO integration pipeline" >&2
"$gradle_command" fifoTest
