package com.erudit.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SubscriptionRepository {
    private final JdbcTemplate jdbc;

    public SubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Subscription subscription) {
        jdbc.update("""
                INSERT INTO subscriptions (id, user_id, plan_code, status, start_date, end_date)
                VALUES (?, ?, ?, ?, ?, ?)
                """, subscription.id(), subscription.userId(), subscription.planCode(), subscription.status().name(),
                Timestamp.from(subscription.startDate()), Timestamp.from(subscription.endDate()));
    }

    public Optional<Subscription> findById(UUID id) {
        return jdbc.query("SELECT * FROM subscriptions WHERE id = ?", this::map, id).stream().findFirst();
    }

    public Optional<Subscription> findActiveByUser(UUID userId, Instant at) {
        return jdbc.query("""
                SELECT * FROM subscriptions
                WHERE user_id = ? AND status = 'ACTIVE' AND start_date <= ? AND end_date > ?
                ORDER BY end_date DESC
                LIMIT 1
                """, this::map, userId, Timestamp.from(at), Timestamp.from(at)).stream().findFirst();
    }

    public void updateStatus(UUID id, SubscriptionStatus status) {
        jdbc.update("UPDATE subscriptions SET status = ? WHERE id = ?", status.name(), id);
    }

    private Subscription map(ResultSet rs, int row) throws SQLException {
        return new Subscription(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("plan_code"), SubscriptionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("start_date").toInstant(), rs.getTimestamp("end_date").toInstant());
    }
}
