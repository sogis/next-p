package org.example.qgisfifo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;

/** Proves that the running QGIS deployment emits one complete start event. */
@Tag("integration")
@Tag("feasibility")
class LoggingFeasibilityIntegrationTest {
    private static final Duration LOG_COLLECTION_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void recordsOneCompleteQgisStartEventForADirectHeaderCorrelatedRequest() throws IOException {
        FifoTestConfig config = FifoTestConfig.load();
        verifyCompleteStartEvent(config, config.directUrls().get(0));
    }

    @Test
    void recordsOneCompleteQgisStartEventForAHaproxyHeaderCorrelatedRequest() throws IOException {
        FifoTestConfig config = FifoTestConfig.load();
        verifyCompleteStartEvent(config, config.haproxyUrl());
    }

    private static void verifyCompleteStartEvent(FifoTestConfig config, String endpoint) throws IOException {
        String runId = "feasibility-" + UUID.randomUUID();
        RequestMarker marker = RequestMarker.create(runId);
        Path artifactDirectory = config.artifactRoot().resolve("feasibility").resolve(runId);
        Path rawLog = artifactDirectory.resolve("qgis.log");
        Files.createDirectories(artifactDirectory);
        Files.writeString(artifactDirectory.resolve("request.txt"), requestDescription(config, endpoint, marker), StandardCharsets.UTF_8);

        try {
            new QgisRequestClient(config).send(0, endpoint, Workload.SHORT, marker);
        } catch (AssertionError error) {
            fail(failure("the feasibility request did not complete successfully", config, endpoint, marker,
                artifactDirectory, "request failure: " + error.getMessage()), error);
        }

        DockerComposeLogs.collectQgisLogs(rawLog, LOG_COLLECTION_TIMEOUT);
        List<String> rawLines = Files.readAllLines(rawLog, StandardCharsets.UTF_8);
        List<String> matchingLines = rawLines.stream()
            .filter(line -> line.contains(marker.requestId()))
            .collect(Collectors.toList());
        List<String> startEvents = matchingLines.stream()
            .filter(line -> line.contains("FIFO_REQUEST_STARTED "))
            .collect(Collectors.toList());
        Files.write(artifactDirectory.resolve("matching.log"), matchingLines, StandardCharsets.UTF_8);
        Files.write(artifactDirectory.resolve("start-events.log"), startEvents, StandardCharsets.UTF_8);

        if (startEvents.size() != 1 || !isCompleteCorrelation(startEvents, marker)) {
            fail(failure("expected exactly one complete FIFO_REQUEST_STARTED event", config, endpoint, marker,
                artifactDirectory, excerpt(matchingLines.isEmpty() ? rawLines : matchingLines)));
        }
    }

    private static boolean isCompleteCorrelation(List<String> startEvents, RequestMarker marker) {
        if (startEvents.size() != 1) {
            return false;
        }
        String event = startEvents.get(0);
        return event.contains("\"run_id\":\"" + marker.runId() + "\"")
            && event.contains("\"request_id\":\"" + marker.requestId() + "\"")
            && event.contains("\"request_id_header\":\"" + marker.requestId() + "\"")
            && event.matches(".*\"worker_pid\":[1-9][0-9]*.*")
            && event.matches(".*\"container_hostname\":\"[^\"]+\".*");
    }

    private static String requestDescription(FifoTestConfig config, String endpoint, RequestMarker marker) {
        return "endpoint=" + endpoint + System.lineSeparator()
            + "workload=" + Workload.SHORT + System.lineSeparator()
            + "runId=" + marker.runId() + System.lineSeparator()
            + "requestId=" + marker.requestId() + System.lineSeparator()
            + "configuration=" + config.describe() + System.lineSeparator();
    }

    private static String failure(
        String problem,
        FifoTestConfig config,
        String endpoint,
        RequestMarker marker,
        Path artifactDirectory,
        String excerpt
    ) {
        return "LOGGING FEASIBILITY FAILED: " + problem + System.lineSeparator()
            + "endpoint=" + endpoint + System.lineSeparator()
            + "runId=" + marker.runId() + ", requestId=" + marker.requestId() + System.lineSeparator()
            + "configuration=" + config.describe() + System.lineSeparator()
            + "artifacts=" + artifactDirectory.toAbsolutePath() + System.lineSeparator()
            + "matching log excerpt:" + System.lineSeparator() + excerpt;
    }

    private static String excerpt(List<String> lines) {
        int firstLine = Math.max(0, lines.size() - 20);
        return String.join(System.lineSeparator(), lines.subList(firstLine, lines.size()));
    }
}
