package com.erudit.events;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "clickhouse.enabled", havingValue = "true", matchIfMissing = true)
public class ClickHouseEventStore {
    private final String url;
    private final String username;
    private final String password;

    public ClickHouseEventStore(
            @Value("${clickhouse.url}") String url,
            @Value("${clickhouse.username}") String username,
            @Value("${clickhouse.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @PostConstruct
    public void initializeSchema() throws SQLException, IOException {
        String sql;
        try (var stream = new ClassPathResource("clickhouse/events.sql").getInputStream()) {
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public void write(AnalyticsEvent event) throws SQLException {
        String sql = "INSERT INTO events (event_id, user_id, session_id, event_type, occurred_at, payload) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, event.eventId());
            statement.setString(2, event.userId());
            statement.setString(3, event.sessionId());
            statement.setString(4, event.eventType());
            statement.setTimestamp(5, Timestamp.from(event.occurredAt()));
            statement.setString(6, event.payload());
            statement.executeUpdate();
        }
    }

    public long countByEventId(UUID eventId) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count() FROM events WHERE event_id = toUUID(?)")) {
            statement.setString(1, eventId.toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
