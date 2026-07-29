package org.example.qgisfifo;

import java.util.Objects;

/** One known client-submission position in a concurrent request batch. */
public final class RequestPlan {
    private final String endpoint;
    private final Workload workload;

    public RequestPlan(String endpoint, Workload workload) {
        this.endpoint = requireNonBlank(endpoint, "endpoint");
        this.workload = Objects.requireNonNull(workload, "workload");
    }

    public String endpoint() {
        return endpoint;
    }

    public Workload workload() {
        return workload;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
