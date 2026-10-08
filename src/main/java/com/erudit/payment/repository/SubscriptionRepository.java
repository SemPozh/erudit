package com.erudit.payment.repository;

import com.erudit.payment.model.Subscription;
import com.erudit.payment.model.SubscriptionStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;

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

    public void upsert(Subscription subscription) {
        int updated = jdbc.update("""
                UPDATE subscriptions SET plan_code = ?, status = ?, start_date = ?, end_date = ?
                WHERE id = ? AND user_id = ?
                """, subscription.planCode(), subscription.status().name(),
                Timestamp.from(subscription.startDate()), Timestamp.from(subscription.endDate()),
                subscription.id(), subscription.userId());
        if (updated == 0) save(subscription);
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

    public Optional<Subscription> findLatestByUser(UUID userId) {
        return jdbc.query("""
                SELECT * FROM subscriptions WHERE user_id = ?
                ORDER BY end_date DESC, id LIMIT 1
                """, this::map, userId).stream().findFirst();
    }

    public void updateStatus(UUID id, SubscriptionStatus status) {
        jdbc.update("UPDATE subscriptions SET status = ? WHERE id = ?", status.name(), id);
    }

    public void extend(UUID id, Instant newEndDate) {
        jdbc.update("UPDATE subscriptions SET status = 'ACTIVE', end_date = ? WHERE id = ?",
                Timestamp.from(newEndDate), id);
    }

    public boolean applyStatusEvent(UUID id, SubscriptionStatus status, Instant occurredAt) {
        return jdbc.update("""
                UPDATE subscriptions SET status = ?, provider_event_at = ?
                WHERE id = ? AND (provider_event_at IS NULL OR provider_event_at < ?)
                """, status.name(), Timestamp.from(occurredAt), id, Timestamp.from(occurredAt)) > 0;
    }

    public boolean applyRenewalEvent(UUID id, Instant newEndDate, Instant occurredAt) {
        return jdbc.update("""
                UPDATE subscriptions SET status = 'ACTIVE', end_date = ?, provider_event_at = ?
                WHERE id = ? AND (provider_event_at IS NULL OR provider_event_at < ?)
                """, Timestamp.from(newEndDate), Timestamp.from(occurredAt), id, Timestamp.from(occurredAt)) > 0;
    }

    public int expireEnded(Instant now) {
        return jdbc.update("UPDATE subscriptions SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_date <= ?",
                Timestamp.from(now));
    }

    public List<Subscription> findExpiringBetween(Instant from, Instant to) {
        return jdbc.query("""
                SELECT * FROM subscriptions
                WHERE status = 'ACTIVE' AND end_date > ? AND end_date <= ?
                ORDER BY end_date, id
                """, this::map, Timestamp.from(from), Timestamp.from(to));
    }

    public boolean claimExpiryNotification(UUID subscriptionId, Instant notifiedAt) {
        try {
            jdbc.update("""
                    INSERT INTO subscription_expiry_notifications (subscription_id, notified_at)
                    VALUES (?, ?)
                    """, subscriptionId, Timestamp.from(notifiedAt));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    private Subscription map(ResultSet rs, int row) throws SQLException {
        return new Subscription(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("plan_code"), SubscriptionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("start_date").toInstant(), rs.getTimestamp("end_date").toInstant());
    }
}
