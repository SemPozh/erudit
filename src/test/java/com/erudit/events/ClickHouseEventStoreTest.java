package com.erudit.events;

import com.erudit.events.model.AnalyticsEvent;
import com.erudit.events.model.EventGroup;
import com.erudit.events.repository.ClickHouseEventStore;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ClickHouseEventStoreTest {
    private static final String USERNAME = "erudit";
    private static final String PASSWORD = "erudit";

    @Container
    static final GenericContainer<?> CLICKHOUSE = new GenericContainer<>(
            DockerImageName.parse("clickhouse/clickhouse-server:25.8"))
            .withEnv("CLICKHOUSE_DB", "erudit")
            .withEnv("CLICKHOUSE_USER", USERNAME)
            .withEnv("CLICKHOUSE_PASSWORD", PASSWORD)
            .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
            .withExposedPorts(8123)
            .waitingFor(Wait.forHttp("/ping").forPort(8123));
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
        return "jdbc:clickhouse:http://" + CLICKHOUSE.getHost() + ":"
                + CLICKHOUSE.getMappedPort(8123) + "/erudit";
    }

    private static String username() {
        return USERNAME;
    }

    private static String password() {
        return PASSWORD;
    }
}
