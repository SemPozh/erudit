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

    @Test
    @EnabledIfEnvironmentVariable(named = "CLICKHOUSE_TEST_URL", matches = ".+")
    void aggregatesDeduplicatedEngagementAndLearningAcrossDaysAndFormats() throws Exception {
        ClickHouseEventStore store = new ClickHouseEventStore(
                System.getenv("CLICKHOUSE_TEST_URL"),
                System.getenv("CLICKHOUSE_TEST_USERNAME"),
                System.getenv("CLICKHOUSE_TEST_PASSWORD"));
        store.initializeSchema();
        Instant dayOne = Instant.parse("2026-09-22T10:00:00Z");
        UUID duplicate = UUID.randomUUID();
        store.write(new AnalyticsEvent(duplicate, "aggregate-user", "aggregate-session",
                "content_viewed", dayOne, "{\"format\":\"ARTICLE\",\"durationSeconds\":30}"));
        store.write(new AnalyticsEvent(duplicate, "aggregate-user", "aggregate-session",
                "content_viewed", dayOne, "{\"format\":\"ARTICLE\",\"durationSeconds\":30}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), "aggregate-user", "aggregate-session",
                "content_completed", dayOne.plusSeconds(60),
                "{\"format\":\"ARTICLE\",\"durationSeconds\":120}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), "aggregate-user", "aggregate-session",
                "content_viewed", dayOne.plusSeconds(120),
                "{\"format\":\"VIDEO\",\"durationSeconds\":60}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), "aggregate-user", "aggregate-session",
                "content_viewed", dayOne.plusSeconds(86400),
                "{\"format\":\"ARTICLE\",\"durationSeconds\":30}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), "aggregate-user", "aggregate-session",
                "quiz_answered", dayOne.plusSeconds(180), "{\"correct\":true}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), "aggregate-user", "aggregate-session",
                "quiz_answered", dayOne.plusSeconds(240), "{\"correct\":false}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), "aggregate-user", "aggregate-session",
                "quiz_completed", dayOne.plusSeconds(300), "{\"durationSeconds\":180}"));
        AnalyticsPeriod period = new AnalyticsPeriod(Instant.parse("2026-09-22T00:00:00Z"),
                Instant.parse("2026-09-24T00:00:00Z"), AnalyticsGranularity.DAY);

        List<AnalyticsMetricPoint> engagement = store.engagement(period);
        assertThat(engagement).hasSize(2);
        assertThat(engagement.getFirst().value()).isEqualTo(2.0);
        assertThat(engagement.getFirst().dimensions())
                .containsEntry("views.ARTICLE", 1.0)
                .containsEntry("views.VIDEO", 1.0)
                .containsEntry("completions.ARTICLE", 1.0);
        assertThat(store.learning(period).getFirst().dimensions())
                .containsEntry("quizCompletions", 1.0)
                .containsEntry("correctRate", 50.0)
                .containsEntry("averageMinutes", 3.0);
    }
}
