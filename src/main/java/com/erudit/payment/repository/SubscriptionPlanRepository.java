package com.erudit.payment.repository;

import com.erudit.payment.model.BillingPeriod;
import com.erudit.payment.model.SubscriptionPlan;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.List;
import java.util.Optional;

@Repository
public class SubscriptionPlanRepository {
    private final JdbcTemplate jdbc;

    public SubscriptionPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SubscriptionPlan> findAll(int page, int size) {
        validatePage(page, size);
        return jdbc.query("""
                        SELECT code, name, price, currency, period
                        FROM subscription_plans ORDER BY price, code LIMIT ? OFFSET ?
                        """,
                (rs, row) -> map(rs.getString("code"), rs.getString("name"), rs.getBigDecimal("price"),
                        rs.getString("currency"), rs.getString("period")), size, page * size);
    }

    public List<SubscriptionPlan> findAvailable(int page, int size) {
        validatePage(page, size);
        return jdbc.query("""
                        SELECT code, name, price, currency, period
                        FROM subscription_plans
                        WHERE available = TRUE
                        ORDER BY price, code
                        LIMIT ? OFFSET ?
                        """,
                (rs, row) -> map(rs.getString("code"), rs.getString("name"), rs.getBigDecimal("price"),
                        rs.getString("currency"), rs.getString("period")), size, page * size);
    }

    public Optional<SubscriptionPlan> findByCode(String code) {
        return jdbc.query("SELECT code, name, price, currency, period FROM subscription_plans WHERE code = ?",
                (rs, row) -> map(rs.getString("code"), rs.getString("name"), rs.getBigDecimal("price"),
                        rs.getString("currency"), rs.getString("period")), code).stream().findFirst();
    }

    public Optional<SubscriptionPlan> findAvailableByCode(String code) {
        return jdbc.query("""
                        SELECT code, name, price, currency, period FROM subscription_plans
                        WHERE code = ? AND available = TRUE
                        """,
                (rs, row) -> map(rs.getString("code"), rs.getString("name"), rs.getBigDecimal("price"),
                        rs.getString("currency"), rs.getString("period")), code).stream().findFirst();
    }

    public void archive(String code) {
        jdbc.update("UPDATE subscription_plans SET available = FALSE WHERE code = ?", code);
    }

    public void save(SubscriptionPlan plan) {
        jdbc.update("INSERT INTO subscription_plans (code, name, price, currency, period) VALUES (?, ?, ?, ?, ?)",
                plan.code(), plan.name(), plan.price(), plan.currency().getCurrencyCode(), plan.period().name());
    }

    private static SubscriptionPlan map(String code, String name, java.math.BigDecimal price,
                                        String currency, String period) {
        return new SubscriptionPlan(code, name, price, Currency.getInstance(currency),
                BillingPeriod.valueOf(period));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 50");
        }
    }
}
