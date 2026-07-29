package org.example.qgisfifo;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Gradle system-property and environment-variable configuration for integration tests. */
public final class FifoTestConfig {
    private static final String DEFAULT_DIRECT_URLS =
        "http://127.0.0.1:18081/ows/,http://127.0.0.1:18082/ows/,"
            + "http://127.0.0.1:18083/ows/,http://127.0.0.1:18084/ows/";
    private static final String DEFAULT_HAPROXY_URL = "http://127.0.0.1:18080/ows/";

    private final List<String> directUrls;
    private final String haproxyUrl;
    private final int batchSize;
    private final int concurrency;
    private final int directRetryBatches;
    private final Duration requestTimeout;
    private final String shortWorkloadQuery;
    private final String heavyWorkloadQuery;

    private FifoTestConfig(
        List<String> directUrls,
        String haproxyUrl,
        int batchSize,
        int concurrency,
        int directRetryBatches,
        Duration requestTimeout,
        String shortWorkloadQuery,
        String heavyWorkloadQuery
    ) {
        this.directUrls = directUrls;
        this.haproxyUrl = haproxyUrl;
        this.batchSize = batchSize;
        this.concurrency = concurrency;
        this.directRetryBatches = directRetryBatches;
        this.requestTimeout = requestTimeout;
        this.shortWorkloadQuery = shortWorkloadQuery;
        this.heavyWorkloadQuery = heavyWorkloadQuery;
    }

    public static FifoTestConfig load() {
        List<String> directUrls = splitUrls(value("fifo.direct.urls", "FIFO_DIRECT_URLS", DEFAULT_DIRECT_URLS));
        return new FifoTestConfig(
            directUrls,
            requiredUrl(value("fifo.haproxy.url", "FIFO_HAPROXY_URL", DEFAULT_HAPROXY_URL)),
            positiveInt(value("fifo.batch.size", "FIFO_BATCH_SIZE", "16"), "batch size"),
            positiveInt(value("fifo.concurrency", "FIFO_CONCURRENCY", "8"), "concurrency"),
            positiveInt(value("fifo.direct.retry.batches", "FIFO_DIRECT_RETRY_BATCHES", "5"), "direct retry batches"),
            Duration.ofSeconds(positiveInt(value("fifo.request.timeout.seconds", "FIFO_REQUEST_TIMEOUT_SECONDS", "60"), "request timeout")),
            value("fifo.workload.short.query", "FIFO_SHORT_WMS_QUERY", Workload.SHORT.defaultQuery()),
            value("fifo.workload.heavy.query", "FIFO_HEAVY_WMS_QUERY", Workload.HEAVY.defaultQuery())
        );
    }

    public List<String> directUrls() {
        return directUrls;
    }

    public String haproxyUrl() {
        return haproxyUrl;
    }

    public int batchSize() {
        return batchSize;
    }

    public int concurrency() {
        return concurrency;
    }

    public int directRetryBatches() {
        return directRetryBatches;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public String shortWorkloadQuery() {
        return shortWorkloadQuery;
    }

    public String heavyWorkloadQuery() {
        return heavyWorkloadQuery;
    }

    private static String value(String propertyName, String environmentName, String defaultValue) {
        String property = System.getProperty(propertyName);
        if (property != null && !property.trim().isEmpty()) {
            return property.trim();
        }
        String environment = System.getenv(environmentName);
        return environment == null || environment.trim().isEmpty() ? defaultValue : environment.trim();
    }

    private static List<String> splitUrls(String commaSeparatedUrls) {
        List<String> urls = Arrays.stream(commaSeparatedUrls.split(","))
            .map(String::trim)
            .filter(url -> !url.isEmpty())
            .map(FifoTestConfig::requiredUrl)
            .collect(Collectors.toList());
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("at least one direct QGIS URL is required");
        }
        return Collections.unmodifiableList(urls);
    }

    private static String requiredUrl(String value) {
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://: " + value);
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private static int positiveInt(String value, String description) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) {
            throw new IllegalArgumentException(description + " must be positive");
        }
        return parsed;
    }
}
