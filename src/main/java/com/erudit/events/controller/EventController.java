package com.erudit.events.controller;

import lombok.RequiredArgsConstructor;
import com.erudit.events.dto.EventIngestionResponse;
import com.erudit.events.service.EventIngestionService;

import com.erudit.openapi.api.EventsApi;
import com.erudit.openapi.model.EventReceiptDataResponse;
import com.erudit.openapi.model.EventReceiptListResponse;
import com.erudit.openapi.model.EventReceiptResponse;
import com.erudit.openapi.model.EventBatchRequest;
import com.erudit.openapi.model.EventIngestionRequest;
import com.erudit.web.exception.UnauthorizedException;
import com.erudit.web.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EventController implements EventsApi {
    private static final int MAX_BATCH_SIZE = 100;
    private final EventIngestionService service;
    private final HttpServletRequest servletRequest;



    @Override
    public ResponseEntity<EventReceiptResponse> ingestEvent(EventIngestionRequest request) {
        EventIngestionResponse result = ingest(request, currentUser());
        return ResponseEntity.accepted().body(new EventReceiptResponse(receipt(result)));
    }

    @Override
    public ResponseEntity<EventReceiptListResponse> ingestEventBatch(EventBatchRequest request) {
        if (request.getEvents() == null || request.getEvents().isEmpty() || request.getEvents().size() > MAX_BATCH_SIZE) {
            throw new ValidationException("events must contain between 1 and 100 items");
        }
        UUID currentUser = currentUser();
        List<EventReceiptDataResponse> receipts = request.getEvents().stream()
                .map(event -> receipt(ingest(event, currentUser))).toList();
        return ResponseEntity.accepted().body(new EventReceiptListResponse(receipts));
    }

    private EventIngestionResponse ingest(EventIngestionRequest request, UUID currentUser) {
        UUID eventUser = request.getUserId() == null ? currentUser : request.getUserId();
        return service.ingest(request.getEventId(), eventUser.toString(), request.getSessionId(),
                request.getEventType(), request.getTimestamp().toInstant(), request.getPayload());
    }

    private UUID currentUser() {
        if (servletRequest.getUserPrincipal() == null) throw new UnauthorizedException("Authentication is required");
        try {
            return UUID.fromString(servletRequest.getUserPrincipal().getName());
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Authenticated user id is invalid");
        }
    }

    private static EventReceiptDataResponse receipt(EventIngestionResponse result) {
        return new EventReceiptDataResponse(result.eventId(), result.accepted());
    }
}

