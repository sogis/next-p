package org.example.qgisfifo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Starts a batch together while retaining the known client submission index. */
public final class ConcurrentBatchSender {
    private final QgisRequestClient client;
    private final FifoTestConfig config;

    public ConcurrentBatchSender(QgisRequestClient client, FifoTestConfig config) {
        this.client = client;
        this.config = config;
    }

    public List<QgisResponse> send(String runId, String endpoint, List<Workload> workloads) {
        if (workloads.isEmpty()) {
            throw new IllegalArgumentException("workloads must not be empty");
        }
        List<RequestPlan> requestPlans = new ArrayList<>();
        for (Workload workload : workloads) {
            requestPlans.add(new RequestPlan(endpoint, workload));
        }
        return send(runId, requestPlans);
    }

    public List<QgisResponse> send(String runId, List<RequestPlan> requestPlans) {
        if (requestPlans.isEmpty()) {
            throw new IllegalArgumentException("request plans must not be empty");
        }
        int workers = Math.min(config.concurrency(), requestPlans.size());
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        try {
            List<Future<QgisResponse>> futures = new ArrayList<>();
            for (int index = 0; index < requestPlans.size(); index++) {
                final int submissionIndex = index;
                final RequestPlan requestPlan = requestPlans.get(index);
                final RequestMarker marker = RequestMarker.create(runId);
                futures.add(executor.submit(requestTask(
                    ready,
                    release,
                    submissionIndex,
                    requestPlan.endpoint(),
                    requestPlan.workload(),
                    marker
                )));
            }
            if (!ready.await(config.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("request tasks did not become ready before the timeout");
            }
            release.countDown();

            List<QgisResponse> responses = new ArrayList<>();
            for (Future<QgisResponse> future : futures) {
                responses.add(future.get(config.requestTimeout().toMillis(), TimeUnit.MILLISECONDS));
            }
            return responses;
        } catch (Exception error) {
            throw new AssertionError("concurrent request batch failed", error);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private Callable<QgisResponse> requestTask(
        CountDownLatch ready,
        CountDownLatch release,
        int submissionIndex,
        String endpoint,
        Workload workload,
        RequestMarker marker
    ) {
        return () -> {
            ready.countDown();
            if (!release.await(config.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("batch release timed out");
            }
            return client.send(submissionIndex, endpoint, workload, marker);
        };
    }
}
