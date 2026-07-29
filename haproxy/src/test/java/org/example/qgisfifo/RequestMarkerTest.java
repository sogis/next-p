package org.example.qgisfifo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestMarkerTest {
    @Test
    void generatesUniqueRequestIdsForTheSameRun() {
        RequestMarker first = RequestMarker.create("run-1");
        RequestMarker second = RequestMarker.create("run-1");

        assertTrue(first.requestId().startsWith("request-"));
        assertNotEquals(first.requestId(), second.requestId());
    }
}
