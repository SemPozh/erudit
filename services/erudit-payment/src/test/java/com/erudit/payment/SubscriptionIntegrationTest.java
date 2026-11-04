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
    @Autowired ProviderPaymentService providers;
    @Autowired MockWebhookVerifier signatures;
    private final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    @Autowired PaymentRepository repository;
    @MockitoBean UserClient users;
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE payment_provider_events, payments, subscriptions, subscription_owners CASCADE");
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

    private java.util.Map<String,Object> event(UUID id, String eventId, String type, Instant at) {
        return java.util.Map.of("subscriptionId",id.toString(),"eventId",eventId,"type",type,"occurredAt",at.toString());
    }
    private void deliver(java.util.Map<String,Object> event) {
        providers.webhook("MOCK",signatures.signature(event),event);
    }
    @Test void receiptHttpReplayCannotExtendOrReactivateAndBindsItsOwner() throws Exception {
        String body = "{\"provider\":\"APP_STORE\",\"receipt\":\"monthly-receipt-87\"}";
        mvc.perform(post("/api/v1/subscriptions/mobile/verify").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        var subscription = repository.latest(USER).orElseThrow();
        service.cancel(USER,subscription.id());
        mvc.perform(post("/api/v1/subscriptions/mobile/verify").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("EXPIRED"));
        assertThat(repository.find(subscription.id()).orElseThrow().endDate()).isEqualTo(subscription.endDate());
        assertThat(repository.paymentCount(USER)).isOne();
        mvc.perform(post("/api/v1/subscriptions/mobile/verify").header("X-User-Id",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/subscriptions/mobile/verify").contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("monthly-receipt-87","invalid-demo"))).andExpect(status().isBadRequest());
    }
    @Test void distinctMobileProvidersAndNewReceiptsAreHandledWithoutShorteningAccess() {
        var yearly = providers.verifyReceipt(USER,"APP_STORE","yearly-receipt-87");
        var monthly = providers.verifyReceipt(USER,"GOOGLE_PLAY","monthly-receipt-87");
        assertThat(monthly.id()).isEqualTo(yearly.id());
        assertThat(monthly.endDate()).isEqualTo(yearly.endDate());
        providers.verifyReceipt(USER,"APP_STORE","monthly-receipt-87");
        assertThat(repository.paymentCount(USER)).isEqualTo(3);
    }
    @Test void receiptCannotActivateWhenUserIsUnavailable() {
        when(users.get(USER)).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> providers.verifyReceipt(USER,"APP_STORE","receipt-87"))
                .isInstanceOfSatisfying(PaymentException.class,e -> assertThat(e.status()).isEqualTo(503));
        assertThat(repository.latest(USER)).isEmpty();
    }
    @Test void webhookHttpChecksSignatureAndDuplicateIdentity() throws Exception {
        var subscription = service.create(USER,"PREMIUM_MONTHLY","mock");
        var event = event(subscription.id(),"renew-87","RENEWED",NOW.plusSeconds(1));
        String body = json.writeValueAsString(event);
        mvc.perform(post("/api/v1/payments/webhooks/MOCK").header("X-Signature","wrong")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        for (int i=0;i<2;i++) mvc.perform(post("/api/v1/payments/webhooks/MOCK").header("X-Signature",signatures.signature(event))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        assertThat(repository.find(subscription.id()).orElseThrow().endDate()).isEqualTo(subscription.endDate().plus(Duration.ofDays(30)));
        assertThat(repository.paymentCount(USER)).isEqualTo(2);
        var conflicting = event(subscription.id(),"renew-87","CANCELLED",NOW.plusSeconds(2));
        assertThatThrownBy(() -> deliver(conflicting)).isInstanceOfSatisfying(PaymentException.class,e -> assertThat(e.status()).isEqualTo(409));
    }
    @Test void staleAndEqualEventsCannotUndoNewerRenewalCancelAndExpiration() {
        var subscription = service.create(USER,"PREMIUM_MONTHLY","mock");
        deliver(event(subscription.id(),"cancel-87","CANCELLED",NOW.plusSeconds(10)));
        assertThat(service.entitlement(USER).premium()).isFalse();
        deliver(event(subscription.id(),"stale-renew-87","RENEWED",NOW.plusSeconds(9)));
        deliver(event(subscription.id(),"equal-renew-87","RENEWED",NOW.plusSeconds(10)));
        assertThat(repository.find(subscription.id()).orElseThrow().status()).isEqualTo("EXPIRED");
        deliver(event(subscription.id(),"new-renew-87","RENEWED",NOW.plusSeconds(11)));
        assertThat(service.entitlement(USER).premium()).isTrue();
        deliver(event(subscription.id(),"stale-expire-87","EXPIRED",NOW.plusSeconds(10)));
        assertThat(service.entitlement(USER).premium()).isTrue();
        deliver(event(subscription.id(),"new-expire-87","EXPIRED",NOW.plusSeconds(12)));
        assertThat(service.entitlement(USER).premium()).isFalse();
        assertThat(repository.paymentCount(USER)).isEqualTo(2);
    }
    @Test void manualCancellationAndNewSubscriptionRejectOldRenewals() {
        var old = service.create(USER,"PREMIUM_MONTHLY","mock");
        service.cancel(USER,old.id());
        deliver(event(old.id(),"older-than-manual","RENEWED",NOW.minusSeconds(1)));
        assertThat(service.entitlement(USER).premium()).isFalse();
        var current = service.create(USER,"PREMIUM_YEARLY","mock");
        deliver(event(old.id(),"old-subscription-renew","RENEWED",NOW.plusSeconds(1)));
        assertThat(service.current(USER).id()).isEqualTo(current.id());
        assertThat(repository.paymentCount(USER)).isEqualTo(2);
    }
    @Test void inactiveTariffDoesNotPreventProviderCancellationOrRenewal() {
        var subscription = service.create(USER,"PREMIUM_YEARLY","mock");
        jdbc.update("UPDATE subscription_plans SET available=false WHERE code='PREMIUM_YEARLY'");
        try {
            deliver(event(subscription.id(),"inactive-renew","RENEWED",NOW.plusSeconds(1)));
            assertThat(repository.find(subscription.id()).orElseThrow().endDate()).isEqualTo(subscription.endDate().plus(Duration.ofDays(365)));
            deliver(event(subscription.id(),"inactive-cancel","CANCELLED",NOW.plusSeconds(2)));
            assertThat(service.entitlement(USER).premium()).isFalse();
        } finally { jdbc.update("UPDATE subscription_plans SET available=true WHERE code='PREMIUM_YEARLY'"); }
    }
    @Test void failedReceiptAndRenewalRollBackIdempotencyAndSubscriptionChanges() {
        var subscription = service.create(USER,"PREMIUM_MONTHLY","mock");
        var event = event(subscription.id(),"retry-after-rollback","RENEWED",NOW.plusSeconds(1));
        jdbc.execute("ALTER TABLE payments ADD CONSTRAINT test_reject CHECK (amount=0) NOT VALID");
        try {
            assertThatThrownBy(() -> deliver(event)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> providers.verifyReceipt(USER,"APP_STORE","receipt-rollback"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(repository.find(subscription.id()).orElseThrow().endDate()).isEqualTo(subscription.endDate());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_provider_events",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mobile_receipts",Integer.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE payments DROP CONSTRAINT test_reject"); }
        deliver(event);
        providers.verifyReceipt(USER,"APP_STORE","receipt-rollback");
        assertThat(repository.paymentCount(USER)).isEqualTo(3);
    }
    @Test void concurrentReceiptOwnersAndWebhookReplaysRemainAtomic() throws Exception {
        UUID other = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            java.util.function.Function<UUID,Boolean> claim = user -> {
                try { start.await(); providers.verifyReceipt(user,"APP_STORE","concurrent-receipt"); return true; }
                catch (PaymentException conflict) { assertThat(conflict.status()).isEqualTo(409); return false; }
                catch (InterruptedException e) { throw new RuntimeException(e); }
            };
            var first = executor.submit(() -> claim.apply(USER)); var second = executor.submit(() -> claim.apply(other));start.countDown();
            assertThat(java.util.List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
            UUID owner = repository.latest(USER).isPresent() ? USER : other;
            var event = event(repository.latest(owner).orElseThrow().id(),"concurrent-webhook","RENEWED",NOW.plusSeconds(1));
            var a=executor.submit(() -> deliver(event)); var b=executor.submit(() -> deliver(event));
            a.get(20,TimeUnit.SECONDS); b.get(20,TimeUnit.SECONDS);
            assertThat(repository.paymentCount(owner)).isEqualTo(2);
        }
    }
    @Test void missingSubscriptionDoesNotConsumeWebhookIdentity() {
        var event=event(UUID.randomUUID(),"unknown-subscription","RENEWED",NOW);
        assertThatThrownBy(() -> deliver(event)).isInstanceOfSatisfying(PaymentException.class,e -> assertThat(e.status()).isEqualTo(404));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_provider_events",Integer.class)).isZero();
    }
    @Test void demoMigrationAndPublishedSwaggerSignatureAreExecutable() throws Exception {
        jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/demo/R__mock_payment_example.sql")));
        UUID id=UUID.fromString("00000000-0000-0000-0000-000000008700");
        assertThat(service.entitlement(USER).premium()).isFalse();
        var event=event(id,"demo-renewal-87","RENEWED",Instant.parse("2026-11-04T12:00:00Z"));
        assertThat(signatures.signature(event)).isEqualTo("4733679dfa15539535a042cc17b59898a29775016d638eca845beb47a1251465");
        deliver(event);
        assertThat(service.entitlement(USER).premium()).isTrue();
    }

    @Test void eventsBeforePurchaseCannotCancelNewPaymentOrReceipt() {
        var bought=service.create(USER,"PREMIUM_MONTHLY","mock");
        deliver(event(bought.id(),"before-purchase","CANCELLED",NOW.minusSeconds(1)));
        assertThat(service.entitlement(USER).premium()).isTrue();
        providers.verifyReceipt(USER,"APP_STORE","new-mobile-purchase");
        deliver(event(bought.id(),"before-receipt","CANCELLED",NOW.minusSeconds(1)));
        assertThat(service.entitlement(USER).premium()).isTrue();
    }

    @Test void maximumProviderEventIdFitsThePaymentReference() {
        var subscription=service.create(USER,"PREMIUM_MONTHLY","mock");
        deliver(event(subscription.id(),"e".repeat(255),"RENEWED",NOW.plusSeconds(1)));
        assertThat(repository.paymentCount(USER)).isEqualTo(2);
    }
}
