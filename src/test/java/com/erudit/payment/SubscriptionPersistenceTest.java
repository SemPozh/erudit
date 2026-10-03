package com.erudit.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionPersistenceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SubscriptionPlanRepository planRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;

    @Test
    void loadsSeedPlansAndFindsOnlyCurrentlyActiveSubscription() {
        assertThat(planRepository.findAll(0, 50)).extracting(SubscriptionPlan::code)
                .containsExactly("PREMIUM_MONTHLY", "PREMIUM_YEARLY");

        UUID userId = createUser();
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        Subscription active = new Subscription(UUID.randomUUID(), userId, "PREMIUM_MONTHLY",
                SubscriptionStatus.ACTIVE, now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS));
        Subscription future = new Subscription(UUID.randomUUID(), userId, "PREMIUM_YEARLY",
                SubscriptionStatus.ACTIVE, now.plus(1, ChronoUnit.DAYS), now.plus(366, ChronoUnit.DAYS));
        subscriptionRepository.save(active);
        subscriptionRepository.save(future);

        assertThat(subscriptionRepository.findById(active.id())).contains(active);
        assertThat(subscriptionRepository.findActiveByUser(userId, now)).contains(active);

        subscriptionRepository.updateStatus(active.id(), SubscriptionStatus.EXPIRED);
        assertThat(subscriptionRepository.findActiveByUser(userId, now)).isEmpty();
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, name, created_at, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, id + "@example.com", "hash", "Subscriber", Timestamp.from(Instant.now()), "ACTIVE");
        return id;
    }
}
