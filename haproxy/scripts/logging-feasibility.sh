#!/usr/bin/env bash
set -euo pipefail

# Exercises the real deployed QGIS image and records the evidence needed
# before ordering assertions can be implemented. The FIFO observability plugin
# emits its event from QgsServerFilter.onRequestReady(), immediately before
# QGIS enters its core-service switch.

compose=(docker compose)
if [[ -n "${COMPOSE_FILE:-}" ]]; then
  compose+=(--file "$COMPOSE_FILE")
fi

run_id="feasibility-$(cat /proc/sys/kernel/random/uuid)"
request_id="request-$(cat /proc/sys/kernel/random/uuid)"
port="${QGIS_1_HTTP_PORT:-18081}"
artifact_dir="${ARTIFACT_DIR:-artifacts}/feasibility/${run_id}"
mkdir -p "$artifact_dir"

url="http://127.0.0.1:${port}/ows/?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetCapabilities&FIFO_RUN_ID=${run_id}&FIFO_REQUEST_ID=${request_id}"

echo "Sending feasibility request ${request_id} to qgis-1"
curl --fail --silent --show-error --connect-timeout 5 --max-time 30 \
  -H "X-Run-Id: ${run_id}" \
  -H "X-Request-Id: ${request_id}" \
  "$url" >"${artifact_dir}/response.xml"

"${compose[@]}" logs --no-color qgis-1 >"${artifact_dir}/qgis-1.log"

if ! grep -F -- "$request_id" "${artifact_dir}/qgis-1.log" >"${artifact_dir}/matching.log"; then
  cat >&2 <<EOF
LOGGING FEASIBILITY FAILED
QGIS did not emit the request marker '${request_id}'.
Raw log: ${artifact_dir}/qgis-1.log

The request did not reach the QGIS observability plugin. Check that the plugin
was loaded and that the upstream HTTP server forwards X-Request-Id.
EOF
  exit 2
fi

if ! grep -F -- 'FIFO_REQUEST_STARTED ' "${artifact_dir}/matching.log" >"${artifact_dir}/start-events.log"; then
  cat >&2 <<EOF
LOGGING FEASIBILITY FAILED
The request marker is present only outside the QGIS execution-start event.
Matching log lines: ${artifact_dir}/matching.log
EOF
  exit 3
fi

if [[ "$(wc -l <"${artifact_dir}/start-events.log")" -ne 1 ]] || \
   ! grep -F -- "\"run_id\":\"${run_id}\"" "${artifact_dir}/start-events.log" >/dev/null || \
   ! grep -F -- "\"request_id\":\"${request_id}\"" "${artifact_dir}/start-events.log" >/dev/null || \
   ! grep -F -- "\"request_id_header\":\"${request_id}\"" "${artifact_dir}/start-events.log" >/dev/null || \
   ! grep -Eq -- '"worker_pid":[1-9][0-9]*' "${artifact_dir}/start-events.log" || \
   ! grep -Eq -- '"container_hostname":"[^"]+"' "${artifact_dir}/start-events.log"; then
  cat >&2 <<EOF
LOGGING FEASIBILITY FAILED
The QGIS start event did not contain exactly one complete correlation record.
Start-event lines: ${artifact_dir}/start-events.log
EOF
  exit 4
fi

echo "Logging feasibility passed; evidence written to ${artifact_dir}"
