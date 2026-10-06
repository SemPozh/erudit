package com.erudit.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;

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

        var publisher = new QueuedAnalyticsEventPublisher(store, Runnable::run,
                Clock.fixed(Instant.parse("2026-09-19T10:00:00Z"), ZoneOffset.UTC));
        UUID publishedId = publisher.publish(new EventPublication(
                EventType.SCREEN_OPENED, "user-1", "session-1", "{\"screen\":\"home\"}"))
                .join();

        assertThat(publishedId).isNotNull();
        assertThat(store.countByEventId(publishedId)).isEqualTo(1);
        assertThat(store.countByGroup(EventGroup.NAVIGATION,
                Instant.parse("2026-09-19T00:00:00Z"), Instant.parse("2026-09-20T00:00:00Z")))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CLICKHOUSE_TEST_URL", matches = ".+")
    void aggregatesUniqueUsersAndSessionsByUtcDay() throws Exception {
        ClickHouseEventStore store = new ClickHouseEventStore(
                System.getenv("CLICKHOUSE_TEST_URL"),
                System.getenv("CLICKHOUSE_TEST_USERNAME"),
                System.getenv("CLICKHOUSE_TEST_PASSWORD"));
        store.initializeSchema();
        Instant first = Instant.parse("2026-09-20T10:00:00Z");
        store.write(new AnalyticsEvent(UUID.randomUUID(), "metrics-user-a", "metrics-session-a",
                "screen_opened", first, "{}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), "metrics-user-a", "metrics-session-b",
                "content_viewed", first.plusSeconds(60), "{}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), "metrics-user-b", "metrics-session-c",
                "content_viewed", first.plusSeconds(120), "{}"));
        AnalyticsPeriod period = new AnalyticsPeriod(Instant.parse("2026-09-20T00:00:00Z"),
                Instant.parse("2026-09-21T00:00:00Z"), AnalyticsGranularity.DAY);

        assertThat(store.activeUsers(period)).extracting(AnalyticsMetricPoint::value).contains(2.0);
        assertThat(store.sessions(period)).extracting(AnalyticsMetricPoint::value).contains(3.0);
    }
}
