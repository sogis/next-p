package org.example.qgisfifo;

import java.time.Duration;

/** HTTP evidence retained by later log-order assertions. */
public final class QgisResponse {
    private final int submissionIndex;
    private final RequestMarker marker;
    private final Workload workload;
    private final String endpoint;
    private final int statusCode;
    private final String contentType;
    private final Duration elapsed;

    public QgisResponse(
        int submissionIndex,
        RequestMarker marker,
        Workload workload,
        String endpoint,
        int statusCode,
        String contentType,
        Duration elapsed
    ) {
        this.submissionIndex = submissionIndex;
        this.marker = marker;
        this.workload = workload;
        this.endpoint = endpoint;
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.elapsed = elapsed;
    }

    public int submissionIndex() { return submissionIndex; }
    public RequestMarker marker() { return marker; }
    public Workload workload() { return workload; }
    public String endpoint() { return endpoint; }
    public int statusCode() { return statusCode; }
    public String contentType() { return contentType; }
    public Duration elapsed() { return elapsed; }
}
