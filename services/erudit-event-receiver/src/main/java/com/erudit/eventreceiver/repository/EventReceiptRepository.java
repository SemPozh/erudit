package com.erudit.eventreceiver.repository;

import com.erudit.eventreceiver.model.EventCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
@Repository
@RequiredArgsConstructor
public class EventReceiptRepository {
    private final DatabaseClient database;
    private final ObjectMapper mapper;

    public Mono<Boolean> insertIfAbsent(EventCommand event) {
        return database.sql("""
                INSERT INTO event_receipts(event_id,user_id,event_type,occurred_at,payload,status)
                VALUES(:id,:user,:type,:occurred,:payload,'PENDING') ON CONFLICT(event_id) DO NOTHING
                """).bind("id", event.eventId()).bind("user", event.userId() == null ? io.r2dbc.spi.Parameters.in(Object.class) : event.userId())
                .bind("type", event.eventType()).bind("occurred", event.occurredAt())
                .bind("payload", json(event.payload())).fetch().rowsUpdated().map(rows -> rows == 1);
    }

    public Mono<Void> delivered(java.util.UUID id) {
        return database.sql("UPDATE event_receipts SET status='DELIVERED',attempts=attempts+1,last_error=NULL WHERE event_id=:id")
                .bind("id", id).fetch().rowsUpdated().then();
    }

    public Mono<Void> failed(java.util.UUID id, String error) {
        return database.sql("UPDATE event_receipts SET status='DEAD_LETTER',attempts=attempts+1,last_error=:error WHERE event_id=:id")
                .bind("id", id).bind("error", error.substring(0, Math.min(1000, error.length())))
                .fetch().rowsUpdated().then();
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid event payload", exception); }
    }
}
