# Implementation Steps

1. Define the test contract.

   - State: completed
   - Direct requests target four individually addressable QGIS replicas.
   - HAProxy is the treatment endpoint.
   - FIFO concerns QGIS execution-start order, never response order.
   - All four QGIS backends are active; each HAProxy-to-QGIS connection has
     `maxconn 1`.

2. Create the Docker Compose foundation.

   - State: completed
   - QGIS Server and HAProxy images are pinned.
   - Four QGIS replicas, HAProxy, published ports, and real QGIS health checks
     are configured.
   - QGIS requests use the image's FastCGI endpoint, `/ows/`.

3. Configure request correlation.

   - State: completed
   - Requests carry `X-Run-Id` and `X-Request-Id`.
   - The Nginx FastCGI parameter configuration forwards those headers to QGIS.
   - The feasibility probe also sends matching query markers for diagnostics.

4. Implement and validate QGIS execution-start observability.

   - State: completed
   - The `fifo_observability` QGIS Server plugin is installed.
   - Its `onRequestReady()` filter emits exactly one structured
     `FIFO_REQUEST_STARTED` record before QGIS enters its core-service switch.
   - Each event includes run/request IDs, QGIS worker PID, container hostname,
     requested service, and operation.
   - The logging-feasibility probe validates complete correlation records.
   - The probe passed both direct-QGIS and HAProxy paths.

5. Implement authoritative HAProxy arrival-order logging.

   - State: completed
   - A request-stage HAProxy Lua action emits a monotonically increasing
     `arrival_sequence` before backend selection.
   - Each `FIFO_HAPROXY_ACCEPTED` event contains the run ID and request ID.
   - The action uses one shared Lua state with HAProxy configured for one
     thread; ordinary HAProxy transaction-log order remains non-authoritative.

6. Create deterministic workloads and test data.

   - State: completed
   - The fixed `fifo_grid` GeoJSON layer is served by the QGIS fixture project.
   - A no-render `GetCapabilities` request is defined as the short workload.
   - A 3072×3072 `GetMap` render is defined as the processing-heavy workload.
   - The query definitions are configuration-driven for resource-constrained
     local or CI environments.

7. Create the Java Gradle integration-test project.

   - State: completed
   - The root Gradle project targets Java 21 and uses JUnit 5 and Rest Assured.
   - Request markers are unique per run and the batch sender releases requests
     concurrently while preserving client submission indexes.
   - URLs, workload queries, batch size, concurrency, timeouts, and retry
     limits are configurable through Gradle system properties or environment variables.

8. Add log collection and parsers.

   - State: pending
   - Retain unmodified HAProxy and QGIS logs for each test run.
   - Parse HAProxy acceptance sequence and QGIS start sequence by request ID.
   - Add parser unit tests for valid, missing, duplicate, and malformed events.

9. Move the logging-feasibility probe into the Gradle test suite.

   - State: completed
   - `loggingFeasibilityTest` sends correlated requests directly and through
     HAProxy, retains raw QGIS logs, and verifies exactly one complete start
     event for each route.
   - `fifoTest` begins with the feasibility task, so it is the prerequisite for
     every later FIFO assertion task.
   - Failures include matching log excerpts, artifact locations, and active
     configuration.

10. Implement the direct-QGIS baseline test.

   - State: pending
   - Send bounded, concurrent mixed-workload batches directly to all replicas.
   - Compare client submission order with QGIS start events.
   - Pass only after observing at least one bounded-run start-order inversion.
   - Report the IDs, worker/container identities, and relevant log excerpts.

11. Implement the HAProxy FIFO test.

   - State: pending
   - Send the mixed workload solely through HAProxy.
   - Compare HAProxy acceptance order with QGIS execution-start order.
   - Fail on every inversion; allow response/completion order to differ.

12. Automate lifecycle, diagnostics, and documentation.

   - State: pending
   - Start Compose, wait for real QGIS health checks, and tear down after tests.
   - Retain artifacts after failures or when a debug-retain option is enabled.
   - Document local and CI commands, required Docker resources, and known
     timing sensitivity of the direct baseline.
