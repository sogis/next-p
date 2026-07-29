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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;

/** Demonstrates that independently addressed QGIS replicas do not ensure global FIFO starts. */
@Tag("integration")
@Tag("direct-baseline")
class DirectQgisBaselineIntegrationTest {
    private static final Duration LOG_COLLECTION_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void observesAStartOrderInversionWithinTheConfiguredRetryBudget() throws IOException {
        FifoTestConfig config = FifoTestConfig.load();
        if (config.batchSize() < config.directUrls().size()) {
            fail("fifo.batch.size must be at least the number of direct QGIS replicas; configuration="
                + config.describe());
        }

        List<String> attemptSummaries = new ArrayList<>();
        for (int attempt = 1; attempt <= config.directRetryBatches(); attempt++) {
            BatchResult result = runBatch(config, attempt);
            if (result.inversion != null) {
                return;
            }
            attemptSummaries.add(result.summary);
        }
        fail("DIRECT QGIS BASELINE FAILED: no execution-start inversion after "
            + config.directRetryBatches() + " bounded batches" + System.lineSeparator()
            + "configuration=" + config.describe() + System.lineSeparator()
            + String.join(System.lineSeparator(), attemptSummaries));
    }

    private static BatchResult runBatch(FifoTestConfig config, int attempt) throws IOException {
        String runId = "direct-baseline-" + UUID.randomUUID();
        List<RequestPlan> plans = mixedPlans(config);
        Path artifactDirectory = config.artifactRoot().resolve("direct-baseline").resolve(runId);
        Path rawLog = artifactDirectory.resolve("qgis.log");
        Files.createDirectories(artifactDirectory);
        Files.writeString(artifactDirectory.resolve("request-plan.txt"), planDescription(config, attempt, runId, plans), StandardCharsets.UTF_8);

        List<QgisResponse> responses;
        try {
            responses = new ConcurrentBatchSender(new QgisRequestClient(config), config).send(runId, plans);
        } catch (AssertionError error) {
            fail("DIRECT QGIS BASELINE FAILED: request batch failed" + System.lineSeparator()
                + "runId=" + runId + System.lineSeparator()
                + "configuration=" + config.describe() + System.lineSeparator()
                + "artifacts=" + artifactDirectory.toAbsolutePath(), error);
            return null;
        }
        Files.writeString(artifactDirectory.resolve("responses.txt"), responseDescription(responses), StandardCharsets.UTF_8);
        DockerComposeLogs.collectQgisLogs(rawLog, LOG_COLLECTION_TIMEOUT);
        List<QgisStartEvent> events = QgisStartEventReader.readForRun(
            Files.readAllLines(rawLog, StandardCharsets.UTF_8), runId);
        validateEvents(plans, responses, events, config, artifactDirectory);

        List<ObservedStart> orderedStarts = observedStarts(plans, responses, events);
        Inversion inversion = findInversion(orderedStarts);
        String summary = batchSummary(attempt, artifactDirectory, orderedStarts, inversion);
        Files.writeString(artifactDirectory.resolve("start-order.txt"), summary, StandardCharsets.UTF_8);
        return new BatchResult(inversion, summary);
    }

    private static List<RequestPlan> mixedPlans(FifoTestConfig config) {
        List<RequestPlan> plans = new ArrayList<>();
        for (int index = 0; index < config.batchSize(); index++) {
            int replicaIndex = index % config.directUrls().size();
            Workload workload = replicaIndex == 0 ? Workload.HEAVY : Workload.SHORT;
            plans.add(new RequestPlan(config.directUrls().get(replicaIndex), workload));
        }
        return plans;
    }

    private static void validateEvents(
        List<RequestPlan> plans,
        List<QgisResponse> responses,
        List<QgisStartEvent> events,
        FifoTestConfig config,
        Path artifactDirectory
    ) {
        Map<String, QgisResponse> responsesByRequestId = responses.stream()
            .collect(Collectors.toMap(response -> response.marker().requestId(), response -> response));
        Set<String> expectedRequestIds = new HashSet<>(responsesByRequestId.keySet());
        Map<String, Long> occurrences = events.stream().collect(Collectors.groupingBy(
            QgisStartEvent::requestId,
            Collectors.counting()
        ));
        Set<String> missing = new HashSet<>(expectedRequestIds);
        missing.removeAll(occurrences.keySet());
        Set<String> duplicate = occurrences.entrySet().stream()
            .filter(entry -> entry.getValue() != 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        Set<String> unexpected = new HashSet<>(occurrences.keySet());
        unexpected.removeAll(expectedRequestIds);
        boolean incomplete = events.stream().anyMatch(event -> !event.isComplete());
        if (!missing.isEmpty() || !duplicate.isEmpty() || !unexpected.isEmpty()
            || incomplete || events.size() != plans.size()) {
            fail("DIRECT QGIS BASELINE FAILED: incomplete execution-start evidence" + System.lineSeparator()
                + "missing=" + missing + ", duplicate=" + duplicate + ", unexpected=" + unexpected
                + ", eventCount=" + events.size() + ", expected=" + plans.size() + System.lineSeparator()
                + "configuration=" + config.describe() + System.lineSeparator()
                + "artifacts=" + artifactDirectory.toAbsolutePath() + System.lineSeparator()
                + eventDescription(events));
        }
    }

    private static List<ObservedStart> observedStarts(
        List<RequestPlan> plans,
        List<QgisResponse> responses,
        List<QgisStartEvent> events
    ) {
        Map<String, QgisResponse> responsesByRequestId = new HashMap<>();
        for (QgisResponse response : responses) {
            responsesByRequestId.put(response.marker().requestId(), response);
        }
        return events.stream()
            .map(event -> {
                QgisResponse response = responsesByRequestId.get(event.requestId());
                return new ObservedStart(response.submissionIndex(), plans.get(response.submissionIndex()), event);
            })
            .sorted(Comparator.comparingLong((ObservedStart start) -> start.event.monotonicNs())
                .thenComparingInt(start -> start.event.sourceIndex()))
            .collect(Collectors.toList());
    }

    private static Inversion findInversion(List<ObservedStart> orderedStarts) {
        ObservedStart latestSubmissionSeen = null;
        for (ObservedStart current : orderedStarts) {
            if (latestSubmissionSeen != null
                && current.submissionIndex < latestSubmissionSeen.submissionIndex
                && current.event.monotonicNs() > latestSubmissionSeen.event.monotonicNs()) {
                return new Inversion(latestSubmissionSeen, current);
            }
            if (latestSubmissionSeen == null
                || current.submissionIndex > latestSubmissionSeen.submissionIndex) {
                latestSubmissionSeen = current;
            }
        }
        return null;
    }

    private static String planDescription(FifoTestConfig config, int attempt, String runId, List<RequestPlan> plans) {
        StringBuilder text = new StringBuilder("attempt=").append(attempt).append(System.lineSeparator())
            .append("runId=").append(runId).append(System.lineSeparator())
            .append("configuration=").append(config.describe()).append(System.lineSeparator());
        for (int index = 0; index < plans.size(); index++) {
            RequestPlan plan = plans.get(index);
            text.append(index).append(' ').append(plan.workload()).append(' ').append(plan.endpoint())
                .append(System.lineSeparator());
        }
        return text.toString();
    }

    private static String responseDescription(List<QgisResponse> responses) {
        return responses.stream()
            .map(response -> response.submissionIndex() + " requestId=" + response.marker().requestId()
                + " workload=" + response.workload() + " endpoint=" + response.endpoint()
                + " elapsed=" + response.elapsed())
            .collect(Collectors.joining(System.lineSeparator(), "", System.lineSeparator()));
    }

    private static String batchSummary(
        int attempt,
        Path artifactDirectory,
        List<ObservedStart> orderedStarts,
        Inversion inversion
    ) {
        StringBuilder text = new StringBuilder("attempt=").append(attempt)
            .append(" artifacts=").append(artifactDirectory.toAbsolutePath()).append(System.lineSeparator());
        for (ObservedStart start : orderedStarts) {
            text.append("submissionIndex=").append(start.submissionIndex)
                .append(" workload=").append(start.plan.workload())
                .append(" endpoint=").append(start.plan.endpoint())
                .append(" monotonicNs=").append(start.event.monotonicNs())
                .append(" requestId=").append(start.event.requestId())
                .append(" workerPid=").append(start.event.workerPid())
                .append(" containerHostname=").append(start.event.containerHostname())
                .append(System.lineSeparator());
        }
        if (inversion != null) {
            text.append("INVERSION laterSubmission=").append(inversion.later.submissionIndex)
                .append(" startedBefore earlierSubmission=").append(inversion.earlier.submissionIndex)
                .append(System.lineSeparator());
        }
        return text.toString();
    }

    private static String eventDescription(List<QgisStartEvent> events) {
        return events.stream().map(QgisStartEvent::rawLine)
            .collect(Collectors.joining(System.lineSeparator()));
    }

    private static final class BatchResult {
        private final Inversion inversion;
        private final String summary;

        private BatchResult(Inversion inversion, String summary) {
            this.inversion = inversion;
            this.summary = summary;
        }
    }

    private static final class ObservedStart {
        private final int submissionIndex;
        private final RequestPlan plan;
        private final QgisStartEvent event;

        private ObservedStart(int submissionIndex, RequestPlan plan, QgisStartEvent event) {
            this.submissionIndex = submissionIndex;
            this.plan = plan;
            this.event = event;
        }
    }

    private static final class Inversion {
        private final ObservedStart later;
        private final ObservedStart earlier;

        private Inversion(ObservedStart later, ObservedStart earlier) {
            this.later = later;
            this.earlier = earlier;
        }
    }
}
