# Project Plan

## Purpose

This project verifies that placing HAProxy in front of Docker-deployed QGIS
Server instances provides FIFO execution of incoming requests. The relevant
order is when QGIS starts handling a request, not when the client receives its
response: an earlier, processing-heavy request may finish after a later one.

The baseline must also demonstrate that the corresponding direct-QGIS setup
does not guarantee global FIFO execution because work can be queued separately
per container or FastCGI process.

## Architecture

The test runner sends requests over one of two paths:

```text
Direct baseline: Rest Assured test runner -> QGIS Server replicas
HAProxy treatment: Rest Assured test runner -> HAProxy -> QGIS Server replicas
```

Docker Compose owns HAProxy, two or more QGIS Server replicas, configuration,
and deterministic test data. The Java test project runs JUnit 5 tests using
Rest Assured and collects the QGIS Server and HAProxy logs after each batch.

The initial design deliberately has no QGIS plugin or external coordinator.
QGIS Server logs are the source of execution-start events; HAProxy logs supply
the request-arrival order for the HAProxy treatment.

## Scope

- Docker Compose configuration for QGIS Server replicas and HAProxy.
- QGIS Server configuration, a small fixed project, and test data that can
  create both short and processing-heavy requests.
- HAProxy configuration with explicit backend connection limits, queue and
  request timeouts, request-ID logging, and an optional stats/admin endpoint.
- A Java Gradle test project using Java 21, JUnit 5, and Rest Assured.
- Log collection and parsing utilities used by the integration tests.

## Test Observability

Each request must carry a unique `X-Request-Id` and a run identifier. Before
implementing FIFO assertions, a logging-feasibility check must prove that the
deployed QGIS version emits a request-start record that includes the request
marker and a QGIS worker, process, or container identity.

Enable QGIS Server request logging to stderr at INFO level for tests. Store raw
container logs as test artifacts. HAProxy must log request IDs at acceptance so
the test can establish the actual HAProxy arrival order rather than infer it
from client response or completion order.

If QGIS logs do not provide an unambiguous, correlatable start record, do not
claim FIFO verification from completion logs. Revisit the test instrumentation,
including the plugin-and-coordinator alternative, before proceeding.

## Test Scenarios

### Direct QGIS Baseline

Bypass HAProxy and send repeated, concurrent bursts of uniquely identified
short and processing-heavy requests directly to the QGIS replicas. Preserve
the client submission order and parse QGIS execution-start records.

The test passes when at least one bounded retry batch contains an inversion: a
later submitted request starts before an earlier submitted request. Capture the
inversion, relevant request IDs, and QGIS worker identities in the test report.

This is an empirical demonstration that this direct-QGIS deployment does not
guarantee global FIFO; it is not a universal claim that every possible direct
QGIS deployment will reorder every workload.

### HAProxy FIFO Verification

Send the workload only through HAProxy. Use HAProxy acceptance logs to
determine request-arrival order, then fill all workers with controlled,
staggered holds and enqueue marked requests behind them. Compare acceptance
order with QGIS delivery order only while exactly one worker becomes free.
When several workers are free, their downstream QGIS start-event order is not
an ordering signal.

QGIS start events prove that a dispatched request entered a worker; they do
not provide a global FIFO assertion across independently free workers. Do not
compare response or completion order.

## Test Script

Use JUnit 5 and Rest Assured for all client requests and response validation.
Use concurrent Java tasks to maintain in-flight load while assigning each
request a known ID. Keep workload shape, replica count, retries, timeouts, and
base URLs configurable through Gradle system properties or environment variables.

The tests must reset their log window and use a unique run identifier so that
events from prior tests cannot affect assertions. Failures must print the
request IDs, observed order, relevant HAProxy/QGIS log excerpts, and the active
configuration.

## Acceptance Criteria

- The logging-feasibility check demonstrates an unambiguous QGIS execution-start
  event for every test request.
- The direct-QGIS baseline captures a start-order inversion within its bounded
  retry budget.
- The HAProxy test releases queued requests in acceptance order under
  controlled single-worker capacity releases.
- All requests either complete within the configured timeout or cause a failure
  with sufficient log evidence to diagnose the fault.
- Response order is never used as a FIFO assertion.

## Constraints And Risks

- The direct baseline is timing-sensitive and can fail to observe an inversion
  on an unusually ordered run; tune workload and retry budget without making
  the test unbounded.
- Test validity depends on the installed QGIS version's log format and on its
  ability to expose request IDs and start events.
- Heavy requests must be deterministic enough to exercise queueing but small
  enough for local and CI timeouts.
- HAProxy request-arrival logging and QGIS start logging are different clocks;
  compare request-ID sequences, not timestamps.
- This setup is test-only and is not production deployment guidance.

## Validation

- Start the Compose stack and wait for QGIS and HAProxy health checks.
- Run the logging-feasibility check before FIFO tests.
- Run the direct-QGIS baseline and HAProxy FIFO tests independently.
- On failure, retain Compose configuration and HAProxy/QGIS logs as artifacts.
- Tear down the Compose stack after tests unless a failure-debug option is set.

## Before Finishing A Change

- Run validation.
- Run tests.
- Summarize the change.
- Summarize any operational risk.
