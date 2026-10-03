package com.erudit.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTypeCatalogTest {
    @Test
    void containsEveryRequiredGroup() {
        assertThat(EventGroup.values()).allSatisfy(group ->
                assertThat(EventTypeCatalog.types(group)).as(group.name()).isNotEmpty());
        assertThat(EventTypeCatalog.types(EventGroup.CONTENT))
                .contains(EventType.CONTENT_VIEWED, EventType.CONTENT_STARTED, EventType.CONTENT_COMPLETED);
    }

    @Test
    void validatesAndClassifiesEventTypeOnEventCreation() {
        AnalyticsEvent event = new AnalyticsEvent(UUID.randomUUID(), "user", "session",
                "payment_succeeded", Instant.now(), "{}");

        assertThat(event.eventGroup()).isEqualTo(EventGroup.MONETIZATION);
        assertThatThrownBy(() -> new AnalyticsEvent(UUID.randomUUID(), "user", "session",
                "made_up_event", Instant.now(), "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown eventType: made_up_event");
    }
}
