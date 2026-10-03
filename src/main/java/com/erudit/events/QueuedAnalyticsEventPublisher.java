package com.erudit.events;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class QueuedAnalyticsEventPublisher implements AnalyticsEventPublisher {
    private final AnalyticsEventSink sink;
    private final Executor executor;
    private final Clock clock;

    public QueuedAnalyticsEventPublisher(AnalyticsEventSink sink, Executor executor, Clock clock) {
        this.sink = sink;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<UUID> publish(EventPublication publication) {
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
