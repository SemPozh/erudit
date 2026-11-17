package com.erudit.eventreceiver.controller;

import com.erudit.eventreceiver.model.EventCommand;
import com.erudit.eventreceiver.openapi.api.EventsApi;
import com.erudit.eventreceiver.openapi.model.EventReceiptResponse;
import com.erudit.eventreceiver.openapi.model.EventRequest;
import com.erudit.eventreceiver.service.EventIngestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class EventController implements EventsApi {
    private final EventIngestionService service;

    @Override
    public Mono<EventReceiptResponse> ingestEvent(Mono<EventRequest> request, ServerWebExchange exchange) {
        return request.map(EventController::command).flatMap(service::ingest).map(EventController::response);
    }

    @Override
    public Flux<EventReceiptResponse> ingestEventBatch(Flux<EventRequest> request, ServerWebExchange exchange) {
        return request.map(EventController::command).collectList().flatMapMany(service::ingest).map(EventController::response);
    }

    static EventCommand command(EventRequest value) {
        return new EventCommand(value.getEventId(), value.getUserId(), value.getEventType(), value.getOccurredAt(), value.getPayload());
    }
    static EventReceiptResponse response(EventIngestionService.Receipt value) {
        return new EventReceiptResponse(value.eventId(), value.status(), value.duplicate());
    }
}
