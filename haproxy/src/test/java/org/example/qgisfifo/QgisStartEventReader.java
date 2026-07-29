package org.example.qgisfifo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the stable fields required by the direct-QGIS baseline assertion. */
public final class QgisStartEventReader {
    private static final String EVENT_PREFIX = "FIFO_REQUEST_STARTED ";

    private QgisStartEventReader() {
    }

    public static List<QgisStartEvent> readForRun(List<String> rawLines, String runId) {
        List<QgisStartEvent> events = new ArrayList<>();
        for (int index = 0; index < rawLines.size(); index++) {
            String rawLine = rawLines.get(index);
            int prefixIndex = rawLine.indexOf(EVENT_PREFIX);
            if (prefixIndex < 0) {
                continue;
            }
            String json = rawLine.substring(prefixIndex + EVENT_PREFIX.length());
            String eventRunId = stringValue(json, "run_id");
            if (!runId.equals(eventRunId)) {
                continue;
            }
            events.add(new QgisStartEvent(
                eventRunId,
                stringValue(json, "request_id"),
                longValue(json, "monotonic_ns"),
                longValue(json, "worker_pid"),
                stringValue(json, "container_hostname"),
                index,
                rawLine
            ));
        }
        return events;
    }

    private static String stringValue(String json, String field) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field)
            + "\\\":\\\"((?:\\\\\\.|[^\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    private static long longValue(String json, String field) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\":([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }
}
