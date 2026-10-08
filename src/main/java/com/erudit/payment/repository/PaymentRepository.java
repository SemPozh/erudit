package com.erudit.payment.repository;

import com.erudit.payment.dto.PaymentPage;
import com.erudit.payment.model.Payment;
import com.erudit.payment.model.PaymentStatus;

import com.erudit.web.exception.ValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Payment payment) {
        jdbc.update("""
                INSERT INTO payments (id, user_id, subscription_id, amount, currency, status,
                                      provider, external_id, created_at, paid_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, payment.id(), payment.userId(), payment.subscriptionId(), payment.amount(),
                payment.currency().getCurrencyCode(), payment.status().name(), payment.provider(),
                payment.externalId(), Timestamp.from(payment.createdAt()), timestamp(payment.paidAt()));
    }

    public PaymentPage findByUser(UUID userId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ValidationException("size must be between 1 and 50");
        }
        List<Payment> items = jdbc.query("""
                SELECT * FROM payments WHERE user_id = ?
                ORDER BY created_at DESC, id LIMIT ? OFFSET ?
                """, this::map, userId, size, page * size);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE user_id = ?", Long.class, userId);
        return new PaymentPage(items, count == null ? 0 : count, page, size);
    }

    public boolean registerEvent(String provider, String eventId, Instant receivedAt) {
        try {
            jdbc.update("INSERT INTO payment_provider_events (provider, event_id, received_at) VALUES (?, ?, ?)",
                    provider, eventId, Timestamp.from(receivedAt));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    private Payment map(ResultSet rs, int row) throws SQLException {
        Timestamp paidAt = rs.getTimestamp("paid_at");
        return new Payment(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("subscription_id", UUID.class), rs.getBigDecimal("amount"),
                Currency.getInstance(rs.getString("currency")), PaymentStatus.valueOf(rs.getString("status")),
                rs.getString("provider"), rs.getString("external_id"),
                rs.getTimestamp("created_at").toInstant(), paidAt == null ? null : paidAt.toInstant());
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}

