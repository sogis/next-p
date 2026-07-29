# QGIS Server FIFO experiment

This project tests execution-start order, not response order, for QGIS Server
requests sent directly to replicas and through HAProxy.

## Current foundation

The Compose stack deliberately exposes four direct QGIS endpoints and one
HAProxy endpoint:

| Route | URL | Purpose |
| --- | --- | --- |
| QGIS replica 1 | `http://localhost:18081/ows/` | Direct baseline |
| QGIS replica 2 | `http://localhost:18082/ows/` | Direct baseline |
| QGIS replica 3 | `http://localhost:18083/ows/` | Direct baseline |
| QGIS replica 4 | `http://localhost:18084/ows/` | Direct baseline |
| HAProxy | `http://localhost:18080/ows/` | Four-replica FIFO treatment |

All four HAProxy backends are active. Each QGIS server has `maxconn 1`, so a
replica handles no more than one active HAProxy connection and excess requests
wait in HAProxy. The FIFO test validates the resulting QGIS start order from
observed events; it does not infer the order from responses.

Copy `.env.example` to `.env` to override ports or deliberately update pinned
image versions.

```bash
docker compose up -d
docker compose ps
scripts/logging-feasibility.sh
```

The `fifo_observability` QGIS Server plugin is mounted read-only at
`/io/plugins`. Its `onRequestReady()` filter emits one structured
`FIFO_REQUEST_STARTED` record after the HTTP request is parsed and immediately
before QGIS enters its core-service switch. The record includes the run/request
markers, QGIS worker PID, and container hostname.

The QGIS image serves its FastCGI endpoint at `/ows/`; `/` is only a static
Nginx landing page. The mounted FastCGI parameter file explicitly forwards the
two correlation headers to QGIS.

The feasibility probe sends a request with `X-Run-Id`, `X-Request-Id`, and
matching query markers. It writes its response and raw QGIS logs beneath
`artifacts/feasibility/`, then verifies exactly one complete start record.

The probe must establish all three conditions before FIFO tests are added:

1. QGIS logs a unique request marker carried in the HTTP header.
2. The marker appears in exactly one `onRequestReady()` start event.
3. That event names the executing worker and container.

The script exits nonzero until those facts are demonstrated. Do not infer FIFO
from completion or HTTP access logs.

To stop the stack:

```bash
docker compose down
```
