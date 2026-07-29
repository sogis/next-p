package org.example.qgisfifo;

/** One QGIS execution-start event retained from the raw container log. */
public final class QgisStartEvent {
    private final String runId;
    private final String requestId;
    private final long monotonicNs;
    private final long workerPid;
    private final String containerHostname;
    private final int sourceIndex;
    private final String rawLine;

    public QgisStartEvent(
        String runId,
        String requestId,
        long monotonicNs,
        long workerPid,
        String containerHostname,
        int sourceIndex,
        String rawLine
    ) {
        this.runId = runId;
        this.requestId = requestId;
        this.monotonicNs = monotonicNs;
        this.workerPid = workerPid;
        this.containerHostname = containerHostname;
        this.sourceIndex = sourceIndex;
        this.rawLine = rawLine;
    }

    public String runId() { return runId; }
    public String requestId() { return requestId; }
    public long monotonicNs() { return monotonicNs; }
    public long workerPid() { return workerPid; }
    public String containerHostname() { return containerHostname; }
    public int sourceIndex() { return sourceIndex; }
    public String rawLine() { return rawLine; }

    public boolean isComplete() {
        return nonBlank(runId) && nonBlank(requestId) && monotonicNs > 0
            && workerPid > 0 && nonBlank(containerHostname);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
