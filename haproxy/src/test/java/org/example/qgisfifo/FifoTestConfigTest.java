package org.example.qgisfifo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FifoTestConfigTest {
    @Test
    void suppliesFourDirectReplicaEndpointsByDefault() {
        FifoTestConfig config = FifoTestConfig.load();

        assertEquals(4, config.directUrls().size());
        assertEquals("http://127.0.0.1:18080/ows/", config.haproxyUrl());
        assertEquals(16, config.batchSize());
        assertEquals(8, config.concurrency());
        assertEquals("artifacts", config.artifactRoot().toString());
    }
}
