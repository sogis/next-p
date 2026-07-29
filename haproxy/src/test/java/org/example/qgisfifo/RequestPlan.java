package org.example.qgisfifo;

import java.util.Objects;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** One known client-submission position in a concurrent request batch. */
public final class RequestPlan {
    private final String endpoint;
    private final Workload workload;
    private final Map<String, String> queryParameters;

    public RequestPlan(String endpoint, Workload workload) {
        this(endpoint, workload, Collections.emptyMap());
    }

    public RequestPlan(String endpoint, Workload workload, Map<String, String> queryParameters) {
        this.endpoint = requireNonBlank(endpoint, "endpoint");
        this.workload = Objects.requireNonNull(workload, "workload");
        this.queryParameters = Collections.unmodifiableMap(new HashMap<>(queryParameters));
    }

    public String endpoint() {
        return endpoint;
    }

    public Workload workload() {
        return workload;
    }

    public Map<String, String> queryParameters() {
        return queryParameters;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
