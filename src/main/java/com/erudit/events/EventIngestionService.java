package com.erudit.events;

import com.erudit.web.ServiceUnavailableException;
import com.erudit.web.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class EventIngestionService {
    private static final int MAX_PAYLOAD_BYTES = 65_536;
    private final EventIngestionRepository repository;
    private final ObjectProvider<AnalyticsEventSink> sinkProvider;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventIngestionService(EventIngestionRepository repository,
                                 ObjectProvider<AnalyticsEventSink> sinkProvider, Clock clock) {
        this.repository = repository;
        this.sinkProvider = sinkProvider;
        this.clock = clock;
    }

    public EventIngestionResult ingest(UUID eventId, String userId, String sessionId,
                                       String eventType, Instant occurredAt, Map<String, Object> payload) {
        String serializedPayload = validate(eventId, sessionId, eventType, occurredAt, payload);
        if (!repository.claim(eventId, clock.instant())) return new EventIngestionResult(eventId, false);
        AnalyticsEvent event = new AnalyticsEvent(eventId, userId == null ? "" : userId,
                sessionId, eventType, occurredAt, serializedPayload);
        try {
            AnalyticsEventSink sink = sinkProvider.getIfAvailable();
            if (sink == null) throw new ServiceUnavailableException("Analytics storage is unavailable");
            sink.write(event);
            return new EventIngestionResult(eventId, true);
        } catch (ServiceUnavailableException exception) {
            repository.release(eventId);
            throw exception;
        } catch (Exception exception) {
            repository.release(eventId);
            throw new ServiceUnavailableException("Analytics event could not be stored");
        }
    }

    private String validate(UUID eventId, String sessionId, String eventType,
                            Instant occurredAt, Map<String, Object> payload) {
        if (eventId == null) throw new ValidationException("eventId is required");
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 255) {
            throw new ValidationException("sessionId must contain between 1 and 255 characters");
        }
        try {
            EventTypeCatalog.require(eventType);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(exception.getMessage());
        }
        if (occurredAt == null) throw new ValidationException("timestamp is required");
        if (occurredAt.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
            throw new ValidationException("timestamp cannot be more than five minutes in the future");
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload == null ? Map.of() : payload);
            if (bytes.length > MAX_PAYLOAD_BYTES) throw new ValidationException("payload is too large");
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new ValidationException("payload must be valid JSON");
        }
    }
}

