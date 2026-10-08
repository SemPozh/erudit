package com.erudit.payment;

import com.erudit.payment.model.BillingPeriod;
import com.erudit.payment.model.SubscriptionPlan;
import com.erudit.payment.repository.SubscriptionPlanRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "clickhouse.enabled=false")
@Testcontainers
class SubscriptionPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private SubscriptionPlanRepository planRepository;

    @Test
    void flywayCreatesSeedPlansOnPostgres() {
        assertThat(planRepository.findByCode("PREMIUM_MONTHLY"))
                .get().extracting(SubscriptionPlan::period).isEqualTo(BillingPeriod.MONTH);
    }
}
