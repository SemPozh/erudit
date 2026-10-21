package com.erudit.events;

import com.erudit.events.model.AnalyticsEvent;
import com.erudit.events.model.EventGroup;
import com.erudit.events.repository.ClickHouseEventStore;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseEventStoreTest {
    @Test
    void appliesVersionedMigrationsIdempotently() throws Exception {
        ClickHouseEventStore store = clickHouseStore();

        store.initializeSchema();
        store.initializeSchema();

        try (var connection = DriverManager.getConnection(url(), username(), password());
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT count(DISTINCT version) FROM erudit_schema_migrations FINAL")) {
            result.next();
            assertThat(result.getInt(1)).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void writesAndReadsEventWithItsGroup() throws Exception {
        ClickHouseEventStore store = clickHouseStore();
        store.initializeSchema();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.ofEpochMilli(1_700_000_000_000L
                + Math.floorMod(eventId.getLeastSignificantBits(), 100_000_000_000L));

        store.write(new AnalyticsEvent(eventId, "user-1", "session-1",
                "screen_opened", occurredAt, "{}"));

        assertThat(store.countByEventId(eventId)).isEqualTo(1);
        assertThat(store.countByGroup(EventGroup.NAVIGATION,
                occurredAt, occurredAt.plusMillis(1))).isEqualTo(1);
    }

    private static ClickHouseEventStore clickHouseStore() {
        return new ClickHouseEventStore(url(), username(), password());
    }

    private static String url() {
        return requiredEnvironmentVariable("CLICKHOUSE_TEST_URL");
    }

    private static String username() {
        return requiredEnvironmentVariable("CLICKHOUSE_TEST_USERNAME");
    }

    private static String password() {
        return requiredEnvironmentVariable("CLICKHOUSE_TEST_PASSWORD");
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured to run ClickHouse integration tests");
        }
        return value;
    }
}
