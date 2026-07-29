package org.example.qgisfifo;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.response.Response;

import java.time.Duration;

import static io.restassured.RestAssured.given;

/** Sends and validates one QGIS request using Rest Assured. */
public final class QgisRequestClient {
    private final FifoTestConfig config;

    public QgisRequestClient(FifoTestConfig config) {
        this.config = config;
    }

    public QgisResponse send(int submissionIndex, String endpoint, Workload workload, RequestMarker marker) {
        long startedAt = System.nanoTime();
        int timeoutMillis = Math.toIntExact(config.requestTimeout().toMillis());
        Response response = given()
            .baseUri(endpoint)
            .config(RestAssured.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", timeoutMillis)
                .setParam("http.socket.timeout", timeoutMillis)
                .setParam("http.connection-manager.timeout", timeoutMillis)))
            .header("X-Run-Id", marker.runId())
            .header("X-Request-Id", marker.requestId())
            .queryParam("FIFO_RUN_ID", marker.runId())
            .queryParam("FIFO_REQUEST_ID", marker.requestId())
            .get("?" + workload.query(config));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        String contentType = response.getContentType();
        if (response.statusCode() != 200) {
            throw new AssertionError("request " + marker.requestId() + " to " + endpoint
                + " returned HTTP " + response.statusCode() + ": " + response.asString());
        }
        if (contentType == null || !contentType.toLowerCase().startsWith(workload.expectedContentType())) {
            throw new AssertionError("request " + marker.requestId() + " expected content type "
                + workload.expectedContentType() + " but received " + contentType);
        }
        return new QgisResponse(submissionIndex, marker, workload, endpoint, response.statusCode(), contentType, elapsed);
    }
}
