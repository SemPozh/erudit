package com.erudit.events;

import com.erudit.events.repository.EventIngestionRepository;
import com.erudit.events.service.AnalyticsEventSink;
import com.erudit.events.service.EventIngestionService;
import com.erudit.web.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventIngestionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Test
    void releasesClaimAndPreservesCauseWhenStorageFails() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventIngestionRepository repository = mock(EventIngestionRepository.class);
        AnalyticsEventSink sink = mock(AnalyticsEventSink.class);
        when(repository.claim(eventId, NOW)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("ClickHouse unavailable"))
                .when(sink).write(any());

        EventIngestionService service = service(repository, sink);

        assertThatThrownBy(() -> ingest(service, eventId))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Analytics event could not be stored")
                .hasRootCauseMessage("ClickHouse unavailable");
        verify(repository).release(eventId);
    }

    @Test
    void releasesClaimWhenStorageIsUnavailable() {
        UUID eventId = UUID.randomUUID();
        EventIngestionRepository repository = mock(EventIngestionRepository.class);
        when(repository.claim(eventId, NOW)).thenReturn(true);
        EventIngestionService service = service(repository, null);

        assertThatThrownBy(() -> ingest(service, eventId))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Analytics storage is unavailable");
        verify(repository).release(eventId);
    }

    @Test
    void reportsDuplicateWithoutWritingItAgain() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventIngestionRepository repository = mock(EventIngestionRepository.class);
        AnalyticsEventSink sink = mock(AnalyticsEventSink.class);
        when(repository.claim(eventId, NOW)).thenReturn(false);

        var result = ingest(service(repository, sink), eventId);

        assertThat(result.accepted()).isFalse();
        verify(sink, never()).write(any());
    }

    private static com.erudit.events.dto.EventIngestionResponse ingest(
            EventIngestionService service, UUID eventId) {
        return service.ingest(eventId, "user-1", "session-1", "content_viewed", NOW, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static EventIngestionService service(EventIngestionRepository repository, AnalyticsEventSink sink) {
        ObjectProvider<AnalyticsEventSink> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sink);
        return new EventIngestionService(repository, provider, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
