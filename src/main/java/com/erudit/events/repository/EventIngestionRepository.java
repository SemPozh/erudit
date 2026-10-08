package com.erudit.events.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class EventIngestionRepository {
    private final JdbcTemplate jdbc;

    public EventIngestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(UUID eventId, Instant receivedAt) {
        try {
            jdbc.update("INSERT INTO event_ingestion_receipts (event_id, received_at) VALUES (?, ?)",
                    eventId, Timestamp.from(receivedAt));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public void release(UUID eventId) {
        jdbc.update("DELETE FROM event_ingestion_receipts WHERE event_id = ?", eventId);
    }
}

