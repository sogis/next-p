package org.example.qgisfifo;

import java.util.Objects;
import java.util.UUID;

/** Correlation values sent with every test request. */
public final class RequestMarker {
    private final String runId;
    private final String requestId;

    private RequestMarker(String runId, String requestId) {
        this.runId = requireNonBlank(runId, "runId");
        this.requestId = requireNonBlank(requestId, "requestId");
    }

    public static RequestMarker create(String runId) {
        return new RequestMarker(runId, "request-" + UUID.randomUUID());
    }

    public String runId() {
        return runId;
    }

    public String requestId() {
        return requestId;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
