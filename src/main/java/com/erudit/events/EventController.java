package com.erudit.events;

import com.erudit.openapi.api.EventsApi;
import com.erudit.openapi.model.EventReceipt;
import com.erudit.openapi.model.EventReceiptListResponse;
import com.erudit.openapi.model.EventReceiptResponse;
import com.erudit.openapi.model.EventRequest;
import com.erudit.openapi.model.EventsBatchRequest;
import com.erudit.web.ForbiddenException;
import com.erudit.web.UnauthorizedException;
import com.erudit.web.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class EventController implements EventsApi {
    private static final int MAX_BATCH_SIZE = 100;
    private final EventIngestionService service;
    private final HttpServletRequest servletRequest;

    public EventController(EventIngestionService service, HttpServletRequest servletRequest) {
        this.service = service;
        this.servletRequest = servletRequest;
    }

    @Override
    public ResponseEntity<EventReceiptResponse> ingestEvent(EventRequest request) {
        EventIngestionResult result = ingest(request, currentUser());
        return ResponseEntity.accepted().body(new EventReceiptResponse(receipt(result)));
    }

    @Override
    public ResponseEntity<EventReceiptListResponse> ingestEventBatch(EventsBatchRequest request) {
        if (request.getEvents() == null || request.getEvents().isEmpty() || request.getEvents().size() > MAX_BATCH_SIZE) {
            throw new ValidationException("events must contain between 1 and 100 items");
        }
        UUID currentUser = currentUser();
        List<EventReceipt> receipts = request.getEvents().stream()
                .map(event -> receipt(ingest(event, currentUser))).toList();
        return ResponseEntity.accepted().body(new EventReceiptListResponse(receipts));
    }

    private EventIngestionResult ingest(EventRequest request, UUID currentUser) {
        UUID eventUser = request.getUserId() == null ? currentUser : request.getUserId();
        if (!eventUser.equals(currentUser) && !servletRequest.isUserInRole("ADMIN")) {
            throw new ForbiddenException("Events can only be submitted for the authenticated user");
        }
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

    private static EventReceipt receipt(EventIngestionResult result) {
        return new EventReceipt(result.eventId(), result.accepted());
    }
}

