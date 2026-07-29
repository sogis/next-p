package org.example.qgisfifo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests one conceptual HAProxy queue while exactly one QGIS worker is allowed
 * to become free at a time. QGIS start events then prove delivery, rather than
 * imposing an invalid global order on independently free workers.
 */
@Tag("integration")
@Tag("haproxy-fifo")
class HaproxyFifoIntegrationTest {
    private static final Duration LOG_COLLECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final int HOLD_SPACING_MILLIS = 4000;

    @Test
    void releasesQueuedRequestsInHaproxyAcceptanceOrderWhenOneWorkerFreesAtATime() throws Exception {
        FifoTestConfig config = FifoTestConfig.load();
        int workerCount = config.directUrls().size();
        String runId = "haproxy-fifo-" + UUID.randomUUID();
        Path artifactDirectory = config.artifactRoot().resolve("haproxy-fifo").resolve(runId);
        Files.createDirectories(artifactDirectory);

        List<MarkerPlan> blockers = blockerPlans(config, workerCount, runId);
        List<RequestPlan> queuedPlans = queuedPlans(config, workerCount);
        Files.writeString(artifactDirectory.resolve("request-plan.txt"),
            planDescription(config, runId, blockers, queuedPlans), StandardCharsets.UTF_8);

        ExecutorService blockerExecutor = Executors.newFixedThreadPool(workerCount);
        List<Future<QgisResponse>> blockerFutures = new ArrayList<>();
        List<QgisResponse> queuedResponses;
        try {
            CountDownLatch blockerReady = new CountDownLatch(workerCount);
            CountDownLatch releaseBlockers = new CountDownLatch(1);
            QgisRequestClient client = new QgisRequestClient(config);
            for (int index = 0; index < blockers.size(); index++) {
                MarkerPlan blocker = blockers.get(index);
                blockerFutures.add(blockerExecutor.submit(blockerTask(
                    client, index, blocker, blockerReady, releaseBlockers
                )));
            }
            if (!blockerReady.await(config.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                fail("HAPROXY FIFO FAILED: blocker tasks did not become ready; artifacts="
                    + artifactDirectory.toAbsolutePath());
            }
            releaseBlockers.countDown();
            awaitBlockerStarts(runId, blockers, config, artifactDirectory);

            queuedResponses = new ConcurrentBatchSender(client, config).send(runId, queuedPlans);
            List<QgisResponse> blockerResponses = await(blockerFutures, config.requestTimeout());
            List<QgisResponse> allResponses = new ArrayList<>(blockerResponses);
            allResponses.addAll(queuedResponses);
            Files.writeString(artifactDirectory.resolve("responses.txt"), responseDescription(allResponses), StandardCharsets.UTF_8);
        } finally {
            blockerExecutor.shutdownNow();
        }

        Path haproxyLog = artifactDirectory.resolve("haproxy.log");
        Path qgisLog = artifactDirectory.resolve("qgis.log");
        DockerComposeLogs.collectHaproxyLogs(haproxyLog, LOG_COLLECTION_TIMEOUT);
        DockerComposeLogs.collectQgisLogs(qgisLog, LOG_COLLECTION_TIMEOUT);
        List<HaproxyArrivalEvent> arrivals = HaproxyArrivalEventReader.readForRun(
            Files.readAllLines(haproxyLog, StandardCharsets.UTF_8), runId);
        List<QgisStartEvent> starts = QgisStartEventReader.readForRun(
            Files.readAllLines(qgisLog, StandardCharsets.UTF_8), runId);

        List<String> blockerIds = blockers.stream().map(plan -> plan.marker.requestId()).collect(Collectors.toList());
        Set<String> queuedIds = queuedResponses.stream().map(response -> response.marker().requestId())
            .collect(Collectors.toSet());
        validateEvidence(blockerIds, queuedIds, arrivals, starts, config, artifactDirectory);
        List<HaproxyArrivalEvent> queuedArrivals = arrivals.stream()
            .filter(event -> queuedIds.contains(event.requestId()))
            .sorted(Comparator.comparingLong(HaproxyArrivalEvent::arrivalSequence))
            .collect(Collectors.toList());
        List<QgisStartEvent> queuedStarts = starts.stream()
            .filter(event -> queuedIds.contains(event.requestId()))
            .sorted(Comparator.comparingLong(QgisStartEvent::monotonicNs))
            .collect(Collectors.toList());
        assertSingleReleaseLane(blockerIds, queuedStarts, starts, config, artifactDirectory);
        assertSameRequestOrder(queuedArrivals, queuedStarts, config, artifactDirectory);
        Files.writeString(artifactDirectory.resolve("queue-release-order.txt"),
            releaseDescription(queuedArrivals, queuedStarts), StandardCharsets.UTF_8);
    }

    private static List<MarkerPlan> blockerPlans(FifoTestConfig config, int workerCount, String runId) {
        List<MarkerPlan> plans = new ArrayList<>();
        for (int index = 0; index < workerCount; index++) {
            int holdMillis = HOLD_SPACING_MILLIS * (index + 1);
            RequestPlan plan = new RequestPlan(config.haproxyUrl(), Workload.SHORT,
                Map.of("FIFO_HOLD_MS", Integer.toString(holdMillis)));
            plans.add(new MarkerPlan(plan, RequestMarker.create(runId)));
        }
        return plans;
    }

    private static List<RequestPlan> queuedPlans(FifoTestConfig config, int workerCount) {
        int queueSize = Math.min(workerCount, config.batchSize());
        List<RequestPlan> plans = new ArrayList<>();
        for (int index = 0; index < queueSize; index++) {
            plans.add(new RequestPlan(config.haproxyUrl(), Workload.SHORT));
        }
        return plans;
    }

    private static Callable<QgisResponse> blockerTask(
        QgisRequestClient client,
        int submissionIndex,
        MarkerPlan blocker,
        CountDownLatch ready,
        CountDownLatch release
    ) {
        return () -> {
            ready.countDown();
            if (!release.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError("blocker release timed out");
            }
            return client.send(submissionIndex, blocker.plan.endpoint(), blocker.plan.workload(),
                blocker.marker, blocker.plan.queryParameters());
        };
    }

    private static List<QgisResponse> await(List<Future<QgisResponse>> futures, Duration timeout) {
        try {
            List<QgisResponse> responses = new ArrayList<>();
            for (Future<QgisResponse> future : futures) {
                responses.add(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
            }
            return responses;
        } catch (Exception error) {
            throw new AssertionError("controlled blocker request failed", error);
        }
    }

    private static void awaitBlockerStarts(
        String runId,
        List<MarkerPlan> blockers,
        FifoTestConfig config,
        Path artifactDirectory
    ) throws IOException, InterruptedException {
        Set<String> expectedIds = blockers.stream().map(plan -> plan.marker.requestId())
            .collect(Collectors.toSet());
        Path readinessLog = artifactDirectory.resolve("qgis-before-queue.log");
        long deadlineNanos = System.nanoTime() + config.requestTimeout().toNanos();
        while (System.nanoTime() < deadlineNanos) {
            DockerComposeLogs.collectQgisLogs(readinessLog, LOG_COLLECTION_TIMEOUT);
            Set<String> observedIds = QgisStartEventReader.readForRun(
                Files.readAllLines(readinessLog, StandardCharsets.UTF_8), runId).stream()
                .map(QgisStartEvent::requestId)
                .collect(Collectors.toSet());
            if (observedIds.containsAll(expectedIds)) {
                return;
            }
            Thread.sleep(100);
        }
        fail("HAPROXY FIFO FAILED: all worker blockers did not reach QGIS before queueing"
            + System.lineSeparator() + "configuration=" + config.describe() + System.lineSeparator()
            + "artifacts=" + artifactDirectory.toAbsolutePath());
    }

    private static void validateEvidence(
        List<String> blockerIds,
        Set<String> queuedIds,
        List<HaproxyArrivalEvent> arrivals,
        List<QgisStartEvent> starts,
        FifoTestConfig config,
        Path artifactDirectory
    ) {
        Set<String> expected = new HashSet<>(blockerIds);
        expected.addAll(queuedIds);
        Set<String> arrivalIds = arrivals.stream().map(HaproxyArrivalEvent::requestId).collect(Collectors.toSet());
        Set<String> startIds = starts.stream().map(QgisStartEvent::requestId).collect(Collectors.toSet());
        boolean complete = arrivals.size() == expected.size() && starts.size() == expected.size()
            && arrivalIds.equals(expected) && startIds.equals(expected)
            && arrivals.stream().allMatch(HaproxyArrivalEvent::isComplete)
            && starts.stream().allMatch(QgisStartEvent::isComplete);
        if (!complete) {
            fail("HAPROXY FIFO FAILED: incomplete correlation evidence" + System.lineSeparator()
                + "expected=" + expected + System.lineSeparator()
                + "arrivalIds=" + arrivalIds + ", startIds=" + startIds + System.lineSeparator()
                + "configuration=" + config.describe() + System.lineSeparator()
                + "artifacts=" + artifactDirectory.toAbsolutePath());
        }
    }

    private static void assertSingleReleaseLane(
        List<String> blockerIds,
        List<QgisStartEvent> queuedStarts,
        List<QgisStartEvent> allStarts,
        FifoTestConfig config,
        Path artifactDirectory
    ) {
        List<QgisStartEvent> blockerStarts = allStarts.stream()
            .filter(start -> blockerIds.contains(start.requestId()))
            .collect(Collectors.toList());
        long latestBlockerStart = blockerStarts.stream().mapToLong(QgisStartEvent::monotonicNs).max().orElse(-1);
        Set<String> blockerWorkers = blockerStarts.stream().map(QgisStartEvent::containerHostname).collect(Collectors.toSet());
        Set<String> queueWorkers = queuedStarts.stream().map(QgisStartEvent::containerHostname).collect(Collectors.toSet());
        boolean queueAfterBlockers = queuedStarts.stream().allMatch(start -> start.monotonicNs() > latestBlockerStart);
        if (blockerWorkers.size() != blockerIds.size() || !queueAfterBlockers || queueWorkers.size() != 1) {
            fail("HAPROXY FIFO FAILED: controlled single-release precondition was not established" + System.lineSeparator()
                + "blockerWorkers=" + blockerWorkers + ", queueWorkers=" + queueWorkers
                + ", queueAfterBlockers=" + queueAfterBlockers + System.lineSeparator()
                + "configuration=" + config.describe() + System.lineSeparator()
                + "artifacts=" + artifactDirectory.toAbsolutePath());
        }
    }

    private static void assertSameRequestOrder(
        List<HaproxyArrivalEvent> queuedArrivals,
        List<QgisStartEvent> queuedStarts,
        FifoTestConfig config,
        Path artifactDirectory
    ) {
        List<String> arrivalIds = queuedArrivals.stream().map(HaproxyArrivalEvent::requestId).collect(Collectors.toList());
        List<String> startIds = queuedStarts.stream().map(QgisStartEvent::requestId).collect(Collectors.toList());
        if (!arrivalIds.equals(startIds)) {
            fail("HAPROXY FIFO FAILED: the controlled single-worker release order differs from HAProxy acceptance order"
                + System.lineSeparator() + "arrivalOrder=" + arrivalIds + System.lineSeparator()
                + "qgisStartOrder=" + startIds + System.lineSeparator()
                + "configuration=" + config.describe() + System.lineSeparator()
                + "artifacts=" + artifactDirectory.toAbsolutePath());
        }
    }

    private static String planDescription(
        FifoTestConfig config,
        String runId,
        List<MarkerPlan> blockers,
        List<RequestPlan> queuedPlans
    ) {
        StringBuilder text = new StringBuilder("runId=").append(runId).append(System.lineSeparator())
            .append("configuration=").append(config.describe()).append(System.lineSeparator());
        for (MarkerPlan blocker : blockers) {
            text.append("blocker requestId=").append(blocker.marker.requestId())
                .append(" holdMs=").append(blocker.plan.queryParameters().get("FIFO_HOLD_MS"))
                .append(System.lineSeparator());
        }
        text.append("queuedRequests=").append(queuedPlans.size()).append(System.lineSeparator());
        return text.toString();
    }

    private static String responseDescription(List<QgisResponse> responses) {
        return responses.stream()
            .map(response -> response.submissionIndex() + " requestId=" + response.marker().requestId()
                + " workload=" + response.workload() + " elapsed=" + response.elapsed())
            .collect(Collectors.joining(System.lineSeparator(), "", System.lineSeparator()));
    }

    private static String releaseDescription(
        List<HaproxyArrivalEvent> arrivals,
        List<QgisStartEvent> starts
    ) {
        StringBuilder text = new StringBuilder("HAProxy accepted queued requests:").append(System.lineSeparator());
        for (HaproxyArrivalEvent arrival : arrivals) {
            text.append(arrival.arrivalSequence()).append(' ').append(arrival.requestId()).append(System.lineSeparator());
        }
        text.append("QGIS starts after the single worker release:").append(System.lineSeparator());
        for (QgisStartEvent start : starts) {
            text.append(start.monotonicNs()).append(' ').append(start.requestId())
                .append(" worker=").append(start.containerHostname()).append(System.lineSeparator());
        }
        return text.toString();
    }

    private static final class MarkerPlan {
        private final RequestPlan plan;
        private final RequestMarker marker;

        private MarkerPlan(RequestPlan plan, RequestMarker marker) {
            this.plan = plan;
            this.marker = marker;
        }
    }
}
