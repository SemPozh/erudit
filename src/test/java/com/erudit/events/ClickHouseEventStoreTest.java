package com.erudit.events;

import com.erudit.events.dto.EventPublication;
import com.erudit.events.model.AnalyticsEvent;
import com.erudit.events.model.AnalyticsGranularity;
import com.erudit.events.model.AnalyticsMetricPoint;
import com.erudit.events.model.AnalyticsPeriod;
import com.erudit.events.model.EventGroup;
import com.erudit.events.model.EventType;
import com.erudit.events.repository.ClickHouseEventStore;
import com.erudit.events.service.QueuedAnalyticsEventPublisher;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseEventStoreTest {

    @Test
    void writesAndReadsEvent() throws Exception {
        ClickHouseEventStore store = clickHouseStore();
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
    void aggregatesUniqueUsersAndSessionsByUtcDay() throws Exception {
        ClickHouseEventStore store = clickHouseStore();
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
    void aggregatesDeduplicatedEngagementAndLearningAcrossDaysAndFormats() throws Exception {
        ClickHouseEventStore store = clickHouseStore();
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

    @Test
    void aggregatesBusinessConversionsWithoutDoubleCountingEvents() throws Exception {
        ClickHouseEventStore store = clickHouseStore();
        store.initializeSchema();
        Instant time = Instant.parse("2026-09-25T10:00:00Z");
        String user = "business-metrics-user-" + UUID.randomUUID();
        UUID duplicate = UUID.randomUUID();
        store.write(new AnalyticsEvent(duplicate, user, "business-session", "content_viewed", time, "{}"));
        store.write(new AnalyticsEvent(duplicate, user, "business-session", "content_viewed", time, "{}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), user, "business-session", "content_started",
                time.plusSeconds(10), "{}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), user, "business-session", "content_completed",
                time.plusSeconds(20), "{}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), user, "business-session", "subscription_started",
                time.plusSeconds(30), "{}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), user, "business-session", "payment_succeeded",
                time.plusSeconds(40), "{\"provider\":\"redacted\"}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), user, "business-session", "notification_delivered",
                time.plusSeconds(50), "{}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), user, "business-session", "notification_opened",
                time.plusSeconds(60), "{}"));
        store.write(new AnalyticsEvent(UUID.randomUUID(), user + "-no-delivery", "business-session-2",
                "notification_opened", time.plusSeconds(86400), "{}"));
        AnalyticsPeriod period = new AnalyticsPeriod(Instant.parse("2026-09-25T00:00:00Z"),
                Instant.parse("2026-09-27T00:00:00Z"), AnalyticsGranularity.DAY);

        assertThat(store.funnels(period).getFirst().dimensions())
                .containsEntry("viewedUsers", 1.0).containsEntry("startToCompleteRate", 100.0);
        assertThat(store.monetization(period).getFirst().dimensions())
                .containsEntry("paidUsers", 1.0).containsEntry("checkoutToPaidRate", 100.0)
                .doesNotContainKeys("provider", "paymentMethod");
        List<AnalyticsMetricPoint> notificationPoints = store.notifications(period);
        assertThat(notificationPoints.getFirst().dimensions())
                .containsEntry("openRate", 100.0).containsEntry("returnRate", 100.0);
        assertThat(notificationPoints.get(1).dimensions()).containsEntry("openRate", 0.0);
        assertThat(store.notifications(new AnalyticsPeriod(Instant.parse("2026-09-28T00:00:00Z"),
                Instant.parse("2026-09-29T00:00:00Z"), AnalyticsGranularity.DAY))).isEmpty();
    }

    private static ClickHouseEventStore clickHouseStore() {
        return new ClickHouseEventStore(
                requiredEnvironmentVariable("CLICKHOUSE_TEST_URL"),
                requiredEnvironmentVariable("CLICKHOUSE_TEST_USERNAME"),
                requiredEnvironmentVariable("CLICKHOUSE_TEST_PASSWORD"));
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured to run ClickHouse integration tests");
        }
        return value;
    }
}
