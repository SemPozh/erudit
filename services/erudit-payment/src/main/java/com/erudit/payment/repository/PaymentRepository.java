package com.erudit.payment.repository;

import com.erudit.payment.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    private final JdbcClient jdbc;
    private final JdbcAggregateTemplate aggregates;
    public PaymentRepository(JdbcClient jdbc, JdbcAggregateTemplate aggregates) {
        this.jdbc = jdbc;
        this.aggregates = aggregates;
    }
    public List<SubscriptionPlan> plans() {
        return jdbc.sql("SELECT * FROM subscription_plans WHERE available ORDER BY code LIMIT 50")
                .query(SubscriptionPlan.class).list();
    }
    public Optional<SubscriptionPlan> plan(String code) {
        return jdbc.sql("SELECT * FROM subscription_plans WHERE code=:code AND available")
                .param("code", code).query(SubscriptionPlan.class).optional();
    }
    public void lockOwner(UUID userId) {
        jdbc.sql("INSERT INTO subscription_owners(user_id) VALUES(:user) ON CONFLICT DO NOTHING")
                .param("user", userId).update();
        jdbc.sql("SELECT user_id FROM subscription_owners WHERE user_id=:user FOR UPDATE")
                .param("user", userId).query(UUID.class).single();
    }
    public Subscription save(Subscription value) { return aggregates.insert(value); }
    public Payment save(Payment value) { return aggregates.insert(value); }
    public Optional<Subscription> find(UUID id) {
        return jdbc.sql("SELECT * FROM subscriptions WHERE id=:id").param("id", id)
                .query(Subscription.class).optional();
    }
    public Optional<Subscription> latest(UUID user) {
        return jdbc.sql("SELECT * FROM subscriptions WHERE user_id=:user ORDER BY start_date DESC,revision DESC LIMIT 1")
                .param("user", user).query(Subscription.class).optional();
    }
    public void expire(UUID user, Instant now) {
        jdbc.sql("UPDATE subscriptions SET status='EXPIRED' WHERE user_id=:user AND status='ACTIVE' AND end_date<=:now")
                .param("user", user).param("now", now.atOffset(java.time.ZoneOffset.UTC)).update();
    }
    public int expireAll(Instant now) {
        return jdbc.sql("UPDATE subscriptions SET status='EXPIRED' WHERE status='ACTIVE' AND end_date<=:now")
                .param("now", now.atOffset(java.time.ZoneOffset.UTC)).update();
    }
    public Subscription cancel(UUID id) {
        jdbc.sql("UPDATE subscriptions SET status='EXPIRED' WHERE id=:id").param("id", id).update();
        return find(id).orElseThrow();
    }
    public List<Payment> payments(UUID user, int page, int size) {
        return jdbc.sql("SELECT * FROM payments WHERE user_id=:user ORDER BY created_at DESC,id DESC LIMIT :size OFFSET :offset")
                .param("user", user).param("size", size).param("offset", (long) page * size).query(Payment.class).list();
    }
    public long paymentCount(UUID user) {
        return jdbc.sql("SELECT count(*) FROM payments WHERE user_id=:user").param("user", user).query(Long.class).single();
    }
}
