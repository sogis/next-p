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
wait in HAProxy. The treatment verifies FIFO queue release under controlled
single-worker capacity releases; it does not infer ordering from responses or
from cross-worker QGIS hook timing.

HAProxy's request-stage Lua action emits one structured
`FIFO_HAPROXY_ACCEPTED` event before backend selection. Its increasing
`arrival_sequence` is the authoritative ordering input for the treatment test.

## Workloads

The fixed `fifo_grid` GeoJSON layer is part of the project and has no external
dependencies. The request definitions in `qgis/workloads.env` provide:

- `SHORT_WMS_QUERY`: a `GetCapabilities` request with no map rendering.
- `HEAVY_WMS_QUERY`: a 3072×3072 WMS `GetMap` render of `fifo_grid`.

The heavy query is intentionally configuration-driven. Reduce `WIDTH` and
`HEIGHT` for memory-constrained CI workers, but retain the same request shape.

## Java test harness

The root Gradle project targets Java 21 and contains the JUnit 5 and Rest
Assured test foundation.
It supplies configurable direct/HAProxy URLs, workload queries, batch size,
concurrency, direct retry count, and request timeout through Gradle system properties
(`fifo.*`) or environment variables (`FIFO_*`).

```bash
./gradlew test
./gradlew fifoTest -Dfifo.concurrency=4 -Dfifo.request.timeout.seconds=90
```

The unit tests validate the configuration and request-ID generation. The
external-stack integration tasks are deliberately never up-to-date: every
`fifoTest` invocation reruns its live assertions.

The feasibility test requires the Compose stack to be running and Docker
Compose to be available on the test runner. It exercises both a direct QGIS
route and the HAProxy route, then stores an unmodified aggregate QGIS log,
matching log lines, and request details under
`artifacts/feasibility/<run-id>/`.

## Run the full experiment

```bash
scripts/run-fifo-tests.sh
```

The runner starts the Compose stack, waits for its real health checks, runs
`fifoTest`, and tears the stack down after success. It never deletes
`artifacts/`. On a test failure it leaves the stack running for diagnosis; set
`FIFO_RETAIN_STACK=true` to retain it after a successful run as well.

```bash
FIFO_RETAIN_STACK=true scripts/run-fifo-tests.sh
FIFO_ARTIFACTS_DIR=/tmp/fifo-artifacts scripts/run-fifo-tests.sh
```

To run individual live assertions against an already-running stack:

```bash
./gradlew loggingFeasibilityTest
./gradlew directQgisBaselineTest
./gradlew haproxyFifoTest
./gradlew fifoTest
```

`fifoTest` runs feasibility, the direct-QGIS baseline, and the HAProxy FIFO
assertion in that order. The HAProxy assertion fills every worker with a
staggered test hold, queues marked requests, then verifies their release order
when exactly one worker becomes available. QGIS start events prove delivery to
the released worker; they are not compared globally while several workers are
free. Use `-Dfifo.artifacts.dir=/path/to/artifacts` or
`FIFO_ARTIFACTS_DIR` to change the artifact root.

Copy `.env.example` to `.env` to override ports or deliberately update pinned
image versions.

## Local and CI requirements

- Docker Engine and Docker Compose v2; ports `18080` through `18084` must be
  available to the runner.
- Java 21 on the runner. Use the included Gradle 8.12.1 wrapper
  (`./gradlew`); its first use downloads that pinned Gradle distribution. Set
  `FIFO_GRADLE_COMMAND` when the lifecycle script must use a different Gradle
  executable or location.
- Capacity for five containers (four QGIS servers and HAProxy) plus the
  3072×3072 direct-baseline rendering workload. Reserve a dedicated Docker
  daemon in CI and increase its CPU or memory allocation if the heavy workload
  times out.

The direct baseline is intentionally empirical and timing-sensitive: it may
need its bounded retry budget or heavy workload dimensions tuned for a slower
or faster runner. A failure to observe an inversion is a failed baseline, not
evidence of global direct-QGIS FIFO.

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
