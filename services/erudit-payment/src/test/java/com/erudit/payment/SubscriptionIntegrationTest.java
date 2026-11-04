package com.erudit.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.erudit.integration.InternalClients.UserClient;
import com.erudit.integration.InternalContracts.UserLookup;
import com.erudit.payment.repository.PaymentRepository;
import com.erudit.payment.service.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties={"eureka.client.enabled=false", "spring.cloud.config.enabled=false",
        "spring.config.name=isolated-test", "erudit.payments.maintenance-initial-delay=PT24H"})
class SubscriptionIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SubscriptionService service;
    @Autowired SubscriptionMaintenance maintenance;
    @Autowired PaymentRepository repository;
    @MockitoBean UserClient users;
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE payments, subscriptions, subscription_owners");
        when(users.get(any())).thenAnswer(call -> new UserLookup(call.getArgument(0), "Demo", true));
    }
    @Test void supportsSwaggerPurchaseCurrentHistoryCancelAndEntitlement() throws Exception {
        mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/subscriptions/plans")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("PREMIUM_MONTHLY"));
        mvc.perform(post("/api/v1/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PREMIUM_MONTHLY\",\"paymentMethodId\":\"mock_card\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        UUID id = repository.latest(USER).orElseThrow().id();
        mvc.perform(get("/api/v1/subscriptions/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
        mvc.perform(get("/internal/v1/entitlements/{userId}", USER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(true));
        mvc.perform(get("/api/v1/subscriptions/payments")).andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].amount").value(299.0))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
        mvc.perform(post("/api/v1/subscriptions/{id}/cancel", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
        mvc.perform(post("/api/v1/subscriptions/{id}/cancel", id)).andExpect(status().isOk());
        mvc.perform(get("/internal/v1/entitlements/{userId}", USER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(false));
    }
    @Test void duplicatePurchaseAndForeignCancellationDoNotChangeState() throws Exception {
        var subscription = service.create(USER,"PREMIUM_YEARLY","mock");
        assertThat(subscription.endDate()).isEqualTo(NOW.plus(Duration.ofDays(365)));
        assertThatThrownBy(() -> service.create(USER,"PREMIUM_MONTHLY","mock"))
                .isInstanceOf(PaymentException.class).hasMessageContaining("already");
        mvc.perform(post("/api/v1/subscriptions/{id}/cancel", subscription.id())
                .header("X-User-Id", UUID.randomUUID())).andExpect(status().isForbidden());
        assertThat(repository.paymentCount(USER)).isOne();
        assertThat(service.entitlement(USER).premium()).isTrue();
    }
    @Test void expirationIsEnforcedAtBoundaryWithoutWaitingForScheduler() {
        service.create(USER,"PREMIUM_MONTHLY","mock");
        jdbc.update("UPDATE subscriptions SET start_date=?, end_date=?", NOW.minusSeconds(60).atOffset(ZoneOffset.UTC), NOW.atOffset(ZoneOffset.UTC));
        assertThat(service.entitlement(USER).premium()).isFalse();
        assertThat(service.current(USER).status()).isEqualTo("EXPIRED");
        var renewed = service.create(USER,"PREMIUM_MONTHLY","mock");
        jdbc.update("UPDATE subscriptions SET start_date=?, end_date=? WHERE id=?",
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC), NOW.atOffset(ZoneOffset.UTC), renewed.id());
        maintenance.expire();
        assertThat(repository.find(renewed.id()).orElseThrow().status()).isEqualTo("EXPIRED");
    }
    @Test void declinedPaymentIsRecordedButDoesNotActivateSubscription() throws Exception {
        mvc.perform(post("/api/v1/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PREMIUM_MONTHLY\",\"paymentMethodId\":\"fail_card\"}"))
                .andExpect(status().isConflict());
        assertThat(repository.latest(USER)).isEmpty();
        assertThat(service.payments(USER,0,20).items()).singleElement().satisfies(p -> {
            assertThat(p.status()).isEqualTo("FAILED"); assertThat(p.subscriptionId()).isNull();
        });
        assertThat(service.entitlement(USER).premium()).isFalse();
    }
    @Test void unavailableUserCreatesNeitherSubscriptionNorPayment() throws Exception {
        when(users.get(USER)).thenThrow(new IllegalStateException("offline"));
        mvc.perform(post("/api/v1/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PREMIUM_MONTHLY\",\"paymentMethodId\":\"mock\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.message").exists());
        assertThat(repository.latest(USER)).isEmpty();
        assertThat(repository.paymentCount(USER)).isZero();
    }
    @Test void databaseFailureRollsBackActivationAndPayment() {
        jdbc.execute("ALTER TABLE payments ADD CONSTRAINT test_reject CHECK (amount=0)");
        try {
            assertThatThrownBy(() -> service.create(USER,"PREMIUM_MONTHLY","mock"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(repository.latest(USER)).isEmpty();
            assertThat(repository.paymentCount(USER)).isZero();
        } finally { jdbc.execute("ALTER TABLE payments DROP CONSTRAINT test_reject"); }
    }
    @Test void concurrentPurchasesCreateExactlyOneActiveSubscriptionAndPayment() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> purchase = () -> {
                start.await();
                try { service.create(USER,"PREMIUM_MONTHLY","mock"); return true; }
                catch (PaymentException conflict) { assertThat(conflict.status()).isEqualTo(409); return false; }
            };
            var first = executor.submit(purchase); var second = executor.submit(purchase); start.countDown();
            assertThat(java.util.List.of(first.get(20,TimeUnit.SECONDS), second.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        }
        assertThat(repository.paymentCount(USER)).isOne();
    }
    @Test void invalidInputsMissingResourcesAndPaginationAreHandled() throws Exception {
        mvc.perform(get("/api/v1/subscriptions/me")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/subscriptions/{id}/cancel", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/subscriptions/payments").param("size","51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/subscriptions/payments").param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/subscriptions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/subscriptions/me").header("X-User-Id","invalid")).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> service.create(USER,"UNKNOWN","mock")).isInstanceOf(PaymentException.class);
        service.create(USER,"PREMIUM_MONTHLY","mock");
        assertThat(service.payments(UUID.randomUUID(),0,20).items()).isEmpty();
        assertThat(service.payments(USER,1,1).items()).isEmpty();
        assertThatThrownBy(() -> service.payments(USER,0,0)).isInstanceOf(PaymentException.class);
    }
}
