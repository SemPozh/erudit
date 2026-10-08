package com.erudit.events.repository;

import com.erudit.events.model.AnalyticsEvent;
import com.erudit.events.model.AnalyticsMetricPoint;
import com.erudit.events.model.AnalyticsPeriod;
import com.erudit.events.model.EventGroup;
import com.erudit.events.service.AnalyticsEventSink;

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

    @Override
    public List<AnalyticsMetricPoint> funnels(AnalyticsPeriod period) {
        String sql = "SELECT bucket, countIf(viewed > 0) viewed_users, "
                + "countIf(started > 0) started_users, countIf(completed > 0) completed_users, "
                + "countIf(quiz_started > 0) quiz_started_users, countIf(quiz_completed > 0) quiz_completed_users "
                + "FROM (SELECT " + period.granularity().bucketExpression() + " bucket, user_id, "
                + "countIf(event_type = 'content_viewed') viewed, "
                + "countIf(event_type = 'content_started') started, "
                + "countIf(event_type = 'content_completed') completed, "
                + "countIf(event_type = 'quiz_started') quiz_started, "
                + "countIf(event_type = 'quiz_completed') quiz_completed "
                + "FROM " + deduplicatedSource() + " WHERE user_id != '' GROUP BY bucket, user_id) "
                + "GROUP BY bucket ORDER BY bucket";
        return conversionMetrics(period, sql, "completed_users", List.of(
                new Counter("viewedUsers", "viewed_users"),
                new Counter("startedUsers", "started_users"),
                new Counter("completedUsers", "completed_users"),
                new Counter("quizStartedUsers", "quiz_started_users"),
                new Counter("quizCompletedUsers", "quiz_completed_users"),
                new Conversion("viewToStartRate", "started_users", "viewed_users"),
                new Conversion("startToCompleteRate", "completed_users", "started_users"),
                new Conversion("quizCompletionRate", "quiz_completed_users", "quiz_started_users")));
    }

    @Override
    public List<AnalyticsMetricPoint> monetization(AnalyticsPeriod period) {
        String sql = "SELECT bucket, countIf(checkout > 0) checkout_users, "
                + "countIf(paid > 0) paid_users, countIf(cancelled > 0) cancelled_users, "
                + "countIf(expired > 0) expired_users FROM (SELECT "
                + period.granularity().bucketExpression() + " bucket, user_id, "
                + "countIf(event_type = 'subscription_started') checkout, "
                + "countIf(event_type = 'payment_succeeded') paid, "
                + "countIf(event_type = 'subscription_cancelled') cancelled, "
                + "countIf(event_type = 'subscription_expired') expired "
                + "FROM " + deduplicatedSource() + " WHERE user_id != '' GROUP BY bucket, user_id) "
                + "GROUP BY bucket ORDER BY bucket";
        return conversionMetrics(period, sql, "paid_users", List.of(
                new Counter("checkoutUsers", "checkout_users"),
                new Counter("paidUsers", "paid_users"),
                new Counter("cancelledUsers", "cancelled_users"),
                new Counter("expiredUsers", "expired_users"),
                new Conversion("checkoutToPaidRate", "paid_users", "checkout_users")));
    }

    @Override
    public List<AnalyticsMetricPoint> notifications(AnalyticsPeriod period) {
        String sql = "SELECT bucket, countIf(delivered > 0) delivered_users, "
                + "countIf(opened > 0) opened_users, countIf(clicked > 0) clicked_users, "
                + "countIf(opened > 0 AND returned > 0) returned_users, "
                + "countIf(unsubscribed > 0) unsubscribed_users FROM (SELECT "
                + period.granularity().bucketExpression() + " bucket, user_id, "
                + "countIf(event_type = 'notification_delivered') delivered, "
                + "countIf(event_type = 'notification_opened') opened, "
                + "countIf(event_type = 'notification_clicked') clicked, "
                + "countIf(event_type IN ('content_viewed', 'quiz_started')) returned, "
                + "countIf(event_type = 'notification_unsubscribed') unsubscribed "
                + "FROM " + deduplicatedSource() + " WHERE user_id != '' GROUP BY bucket, user_id) "
                + "GROUP BY bucket ORDER BY bucket";
        return conversionMetrics(period, sql, "opened_users", List.of(
                new Counter("deliveredUsers", "delivered_users"),
                new Counter("openedUsers", "opened_users"),
                new Counter("clickedUsers", "clicked_users"),
                new Counter("returnedUsers", "returned_users"),
                new Counter("unsubscribedUsers", "unsubscribed_users"),
                new Conversion("openRate", "opened_users", "delivered_users"),
                new Conversion("clickThroughRate", "clicked_users", "delivered_users"),
                new Conversion("returnRate", "returned_users", "opened_users"),
                new Conversion("unsubscribeRate", "unsubscribed_users", "delivered_users")));
    }

    private List<AnalyticsMetricPoint> conversionMetrics(AnalyticsPeriod period, String sql,
                                                          String valueColumn, List<Dimension> dimensions) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, period);
            try (ResultSet result = statement.executeQuery()) {
                List<AnalyticsMetricPoint> points = new ArrayList<>();
                while (result.next()) {
                    Map<String, Double> values = new LinkedHashMap<>();
                    for (Dimension dimension : dimensions) dimension.add(result, values);
                    points.add(new AnalyticsMetricPoint(result.getTimestamp("bucket").toInstant(),
                            result.getDouble(valueColumn), Map.copyOf(values)));
                }
                return List.copyOf(points);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("ClickHouse conversion query failed", exception);
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

    private sealed interface Dimension permits Counter, Conversion {
        void add(ResultSet result, Map<String, Double> values) throws SQLException;
    }

    private record Counter(String name, String column) implements Dimension {
        @Override public void add(ResultSet result, Map<String, Double> values) throws SQLException {
            values.put(name, result.getDouble(column));
        }
    }

    private record Conversion(String name, String numerator, String denominator) implements Dimension {
        @Override public void add(ResultSet result, Map<String, Double> values) throws SQLException {
            double total = result.getDouble(denominator);
            values.put(name, total == 0 ? 0 : round(result.getDouble(numerator) * 100 / total));
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
