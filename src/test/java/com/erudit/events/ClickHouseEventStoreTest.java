package com.erudit.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseEventStoreTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "CLICKHOUSE_TEST_URL", matches = ".+")
    void writesAndReadsEvent() throws Exception {
        ClickHouseEventStore store = new ClickHouseEventStore(
                System.getenv("CLICKHOUSE_TEST_URL"),
                System.getenv("CLICKHOUSE_TEST_USERNAME"),
                System.getenv("CLICKHOUSE_TEST_PASSWORD"));
        store.initializeSchema();

        UUID eventId = UUID.randomUUID();
        store.write(new AnalyticsEvent(eventId, "user-1", "session-1", "screen_opened",
                Instant.parse("2026-09-19T10:00:00Z"), "{\"screen\":\"home\"}"));

        assertThat(store.countByEventId(eventId)).isEqualTo(1);
    }
}
