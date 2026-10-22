package com.erudit.events.service;

import lombok.RequiredArgsConstructor;
import com.erudit.events.dto.EventPublicationRequest;
import com.erudit.events.model.AnalyticsEvent;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RequiredArgsConstructor
public class QueuedAnalyticsEventPublisher implements AnalyticsEventPublisher {
    private final AnalyticsEventSink sink;
    private final Executor executor;
    private final Clock clock;



    @Override
    public CompletableFuture<UUID> publish(EventPublicationRequest publication) {
        UUID eventId = UUID.randomUUID();
        AnalyticsEvent event = new AnalyticsEvent(eventId, publication.userId(), publication.sessionId(),
                publication.eventType().value(), clock.instant(), publication.payload());
        CompletableFuture<UUID> result = new CompletableFuture<>();
        try {
            executor.execute(() -> write(event, result));
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    private void write(AnalyticsEvent event, CompletableFuture<UUID> result) {
        try {
            sink.write(event);
            result.complete(event.eventId());
        } catch (Exception exception) {
            result.completeExceptionally(exception);
        }
    }
}
