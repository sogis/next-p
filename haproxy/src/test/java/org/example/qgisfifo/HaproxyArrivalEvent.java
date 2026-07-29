package org.example.qgisfifo;

/** One HAProxy frontend acceptance event emitted before backend selection. */
public final class HaproxyArrivalEvent {
    private final long arrivalSequence;
    private final String runId;
    private final String requestId;
    private final String rawLine;

    public HaproxyArrivalEvent(long arrivalSequence, String runId, String requestId, String rawLine) {
        this.arrivalSequence = arrivalSequence;
        this.runId = runId;
        this.requestId = requestId;
        this.rawLine = rawLine;
    }

    public long arrivalSequence() { return arrivalSequence; }
    public String runId() { return runId; }
    public String requestId() { return requestId; }
    public String rawLine() { return rawLine; }

    public boolean isComplete() {
        return arrivalSequence > 0 && nonBlank(runId) && nonBlank(requestId);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
