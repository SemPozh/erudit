package com.erudit.events;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueuedAnalyticsEventPublisherTest {
    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    void validatesBuildsAndWritesEventThroughCommonPath() {
        AtomicReference<AnalyticsEvent> written = new AtomicReference<>();
        var publisher = new QueuedAnalyticsEventPublisher(written::set, Runnable::run,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var eventId = publisher.publish(new EventPublication(
                EventType.CONTENT_COMPLETED, "user-1", "session-1", "{\"contentId\":\"42\"}"))
                .join();

        assertThat(written.get()).isEqualTo(new AnalyticsEvent(eventId, "user-1", "session-1",
                "content_completed", NOW, "{\"contentId\":\"42\"}"));
    }

    @Test
    void exposesSinkFailureToCaller() {
        var publisher = new QueuedAnalyticsEventPublisher(event -> {
            throw new IllegalStateException("ClickHouse unavailable");
        }, Runnable::run, Clock.systemUTC());

        var result = publisher.publish(new EventPublication(
                EventType.QUIZ_COMPLETED, null, "session-1", "{}"));

        assertThatThrownBy(result::join).hasRootCauseMessage("ClickHouse unavailable");
    }

    @Test
    void rejectsInvalidPublicationBeforeQueueing() {
        assertThatThrownBy(() -> new EventPublication(EventType.SCREEN_OPENED, null, " ", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sessionId is required");
    }
}
