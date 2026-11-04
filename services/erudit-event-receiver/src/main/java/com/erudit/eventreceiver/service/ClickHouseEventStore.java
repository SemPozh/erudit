package com.erudit.eventreceiver.service;

import com.erudit.eventreceiver.model.EventCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.sql.Timestamp;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Component
public class ClickHouseEventStore {
    private final String url;
    private final String username;
    private final String password;
    private final ObjectMapper mapper;
    private final Scheduler jdbcScheduler = Schedulers.newBoundedElastic(4, 256, "clickhouse-jdbc");

    public ClickHouseEventStore(@Value("${erudit.clickhouse.url}") String url,
                                @Value("${erudit.clickhouse.username:default}") String username,
                                @Value("${erudit.clickhouse.password:}") String password, ObjectMapper mapper) {
        this.url = url.startsWith("jdbc:") ? url : "jdbc:clickhouse:" + url;
        this.username = username; this.password = password; this.mapper = mapper;
    }

    public Mono<Void> initialize() {
        return blocking(() -> { try (var connection = DriverManager.getConnection(url, username, password); var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS analytics_events(
                event_id UUID,event_type String,user_id Nullable(UUID),occurred_at DateTime64(3),payload String)
                ENGINE=ReplacingMergeTree ORDER BY(event_id)""");
        } return null; }).then();
    }

    public Mono<Void> write(EventCommand event) {
        return blocking(() -> { try (var connection = DriverManager.getConnection(url, username, password); var statement = connection.prepareStatement(
                "INSERT INTO analytics_events(event_id,event_type,user_id,occurred_at,payload) VALUES(?,?,?,?,?)")) {
            statement.setObject(1, event.eventId()); statement.setString(2, event.eventType()); statement.setObject(3, event.userId());
            statement.setTimestamp(4, Timestamp.from(event.occurredAt().toInstant())); statement.setString(5, mapper.writeValueAsString(event.payload()));
            statement.executeUpdate();
        } return null; }).then();
    }

    public Mono<Aggregate> aggregate(String metric, java.time.OffsetDateTime from, java.time.OffsetDateTime to) {
        String prefix = switch (metric) {
            case "sessions" -> "SESSION_"; case "engagement" -> "CONTENT_"; case "learning" -> "QUIZ_";
            case "monetization" -> "PAYMENT_"; case "notifications" -> "NOTIFICATION_";
            case "active-users", "funnels" -> ""; default -> throw new IllegalArgumentException("Unsupported metric");
        };
        return blocking(() -> { try (var connection = DriverManager.getConnection(url, username, password); var statement = connection.prepareStatement(
                "SELECT count(),uniqExact(user_id) FROM analytics_events WHERE occurred_at>=? AND occurred_at<? AND event_type LIKE ?")) {
            statement.setTimestamp(1, Timestamp.from(from.toInstant())); statement.setTimestamp(2, Timestamp.from(to.toInstant()));
            statement.setString(3, prefix + "%");
            try (var rs = statement.executeQuery()) { rs.next(); long count=rs.getLong(1), users=rs.getLong(2); return new Aggregate(count, users, count == 0 ? 0d : (double) count / Math.max(1, users)); }
        }});
    }

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> action) { return Mono.fromCallable(action).subscribeOn(jdbcScheduler); }
    public record Aggregate(long count, long users, double value) {}
}
