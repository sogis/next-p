package org.example.qgisfifo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts structured HAProxy acceptance events for one test run. */
public final class HaproxyArrivalEventReader {
    private static final String EVENT_PREFIX = "FIFO_HAPROXY_ACCEPTED ";

    private HaproxyArrivalEventReader() {
    }

    public static List<HaproxyArrivalEvent> readForRun(List<String> rawLines, String runId) {
        List<HaproxyArrivalEvent> events = new ArrayList<>();
        for (String rawLine : rawLines) {
            int prefixIndex = rawLine.indexOf(EVENT_PREFIX);
            if (prefixIndex < 0) {
                continue;
            }
            String json = rawLine.substring(prefixIndex + EVENT_PREFIX.length());
            String eventRunId = stringValue(json, "run_id");
            if (!runId.equals(eventRunId)) {
                continue;
            }
            events.add(new HaproxyArrivalEvent(
                longValue(json, "arrival_sequence"),
                eventRunId,
                stringValue(json, "request_id"),
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
