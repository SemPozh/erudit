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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "clickhouse.enabled", havingValue = "true", matchIfMissing = true)
public class ClickHouseEventStore implements AnalyticsEventSink, AnalyticsMetricsRepository {
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
            for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
    }

    @Override
    public void write(AnalyticsEvent event) throws SQLException {
        String sql = "INSERT INTO events (event_id, user_id, session_id, event_type, event_group, occurred_at, payload) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, event.eventId());
            statement.setString(2, event.userId());
            statement.setString(3, event.sessionId());
            statement.setString(4, event.eventType());
            statement.setString(5, event.eventGroup().name());
            statement.setTimestamp(6, Timestamp.from(event.occurredAt()));
            statement.setString(7, event.payload());
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

    public long countByGroup(EventGroup group, Instant from, Instant to) throws SQLException {
        String sql = "SELECT count() FROM events WHERE event_group = ? AND occurred_at >= ? AND occurred_at < ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, group.name());
            statement.setTimestamp(2, Timestamp.from(from));
            statement.setTimestamp(3, Timestamp.from(to));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    @Override
    public List<AnalyticsMetricPoint> activeUsers(AnalyticsPeriod period) {
        return metric(period, "uniqExactIf(user_id, user_id != '')");
    }

    @Override
    public List<AnalyticsMetricPoint> sessions(AnalyticsPeriod period) {
        return metric(period, "uniqExactIf(session_id, session_id != '')");
    }

    @Override
    public List<AnalyticsMetricPoint> engagement(AnalyticsPeriod period) {
        String format = "if(empty(JSONExtractString(payload, 'format')), 'UNKNOWN', "
                + "upperUTF8(JSONExtractString(payload, 'format')))";
        String sql = "SELECT " + period.granularity().bucketExpression() + " bucket, " + format + " content_format, "
                + "countIf(event_type = 'content_viewed') views, "
                + "countIf(event_type = 'content_completed') completions, "
                + "sumIf(JSONExtractFloat(payload, 'durationSeconds'), "
                + "event_type IN ('content_viewed', 'content_completed')) duration_seconds "
                + "FROM " + deduplicatedSource()
                + " GROUP BY bucket, content_format ORDER BY bucket, content_format";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, period);
            try (ResultSet result = statement.executeQuery()) {
                Map<Instant, Map<String, Double>> dimensions = new LinkedHashMap<>();
                while (result.next()) {
                    Instant bucket = result.getTimestamp("bucket").toInstant();
                    String suffix = dimensionSuffix(result.getString("content_format"));
                    double views = result.getDouble("views");
                    double completions = result.getDouble("completions");
                    double minutes = result.getDouble("duration_seconds") / 60.0;
                    Map<String, Double> values = dimensions.computeIfAbsent(bucket, ignored -> new LinkedHashMap<>());
                    values.put("views." + suffix, views);
                    values.put("completions." + suffix, completions);
                    values.put("minutes." + suffix, round(minutes));
                    values.merge("views", views, Double::sum);
                    values.merge("completions", completions, Double::sum);
                    values.merge("minutes", round(minutes), Double::sum);
                }
                return dimensions.entrySet().stream().map(entry -> new AnalyticsMetricPoint(
                        entry.getKey(), entry.getValue().getOrDefault("views", 0.0), entry.getValue())).toList();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("ClickHouse engagement query failed", exception);
        }
    }

    @Override
    public List<AnalyticsMetricPoint> learning(AnalyticsPeriod period) {
        String sql = "SELECT " + period.granularity().bucketExpression() + " bucket, "
                + "countIf(event_type = 'quiz_completed') completions, "
                + "countIf(event_type = 'quiz_answered') answers, "
                + "countIf(event_type = 'quiz_answered' AND JSONExtractBool(payload, 'correct')) correct, "
                + "sumIf(JSONExtractFloat(payload, 'durationSeconds'), event_type = 'quiz_completed') duration_seconds "
                + "FROM " + deduplicatedSource() + " GROUP BY bucket ORDER BY bucket";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, period);
            try (ResultSet result = statement.executeQuery()) {
                List<AnalyticsMetricPoint> points = new ArrayList<>();
                while (result.next()) {
                    double completions = result.getDouble("completions");
                    double answers = result.getDouble("answers");
                    double correct = result.getDouble("correct");
                    double duration = result.getDouble("duration_seconds");
                    Map<String, Double> values = Map.of(
                            "quizCompletions", completions,
                            "correctRate", answers == 0 ? 0 : round(correct * 100 / answers),
                            "averageMinutes", completions == 0 ? 0 : round(duration / 60 / completions));
                    points.add(new AnalyticsMetricPoint(result.getTimestamp("bucket").toInstant(),
                            completions, values));
                }
                return List.copyOf(points);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("ClickHouse learning query failed", exception);
        }
    }

    private List<AnalyticsMetricPoint> metric(AnalyticsPeriod period, String aggregation) {
        String sql = "SELECT " + period.granularity().bucketExpression() + " bucket, "
                + aggregation + " value FROM " + deduplicatedSource() + " "
                + "GROUP BY bucket ORDER BY bucket";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, period);
            try (ResultSet result = statement.executeQuery()) {
                List<AnalyticsMetricPoint> points = new ArrayList<>();
                while (result.next()) {
                    points.add(new AnalyticsMetricPoint(result.getTimestamp("bucket").toInstant(),
                            result.getDouble("value")));
                }
                return List.copyOf(points);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("ClickHouse analytics query failed", exception);
        }
    }

    private static String deduplicatedSource() {
        return "(SELECT * FROM events WHERE occurred_at >= ? AND occurred_at < ? "
                + "ORDER BY occurred_at DESC LIMIT 1 BY event_id) deduplicated";
    }

    private static void bindPeriod(PreparedStatement statement, AnalyticsPeriod period) throws SQLException {
        statement.setTimestamp(1, Timestamp.from(period.from()));
        statement.setTimestamp(2, Timestamp.from(period.to()));
    }

    private static String dimensionSuffix(String value) {
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
