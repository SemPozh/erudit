package com.erudit.eventreceiver.service;

import com.erudit.eventreceiver.model.EventCommand;
import com.erudit.eventreceiver.repository.EventReceiptRepository;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
@RequiredArgsConstructor
public class EventIngestionService {
    private final EventReceiptRepository receipts;
    private final ClickHouseEventStore clickHouse;
    @Value("${erudit.events.pipeline-buffer:1024}") private int buffer;
    @Value("${erudit.events.clickhouse-retries:3}") private int retries;
    private Sinks.Many<EventCommand> pipeline;

    @PostConstruct
    public void start() {
        pipeline = Sinks.many().multicast().onBackpressureBuffer(buffer, false);
        clickHouse.initialize().thenMany(pipeline.asFlux().flatMap(this::store, 4)).subscribe();
    }

    public Mono<Receipt> ingest(EventCommand event) {
        return Mono.defer(() -> {
            validate(event);
            return receipts.insertIfAbsent(event).map(inserted -> {
            if (inserted && pipeline.tryEmitNext(event).isFailure()) throw new IllegalStateException("Event pipeline is full");
            return new Receipt(event.eventId(), inserted ? "ACCEPTED" : "DUPLICATE", !inserted);
            });
        });
    }

    public Flux<Receipt> ingest(List<EventCommand> events) {
        if (events == null || events.isEmpty() || events.size() > 100) return Flux.error(new IllegalArgumentException("Batch size must be 1..100"));
        return Flux.fromIterable(events).limitRate(16).concatMap(this::ingest);
    }

    private Mono<Void> store(EventCommand event) {
        return clickHouse.write(event).retry(retries).then(receipts.delivered(event.eventId()))
                .onErrorResume(error -> receipts.failed(event.eventId(), error.getMessage()));
    }

    private void validate(EventCommand event) {
        if (event == null || event.eventId() == null || event.eventType() == null || event.eventType().isBlank()
                || event.occurredAt() == null || event.occurredAt().isAfter(OffsetDateTime.now().plusMinutes(5)) || event.payload() == null)
            throw new IllegalArgumentException("Invalid event");
    }
    public record Receipt(java.util.UUID eventId, String status, boolean duplicate) {}
}
