package com.erudit.payment;

import com.erudit.payment.model.SubscriptionStatus;
import com.erudit.payment.repository.SubscriptionRepository;
import com.erudit.payment.service.PaymentWebhookService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SubscriptionRepository subscriptions;
    @Autowired private PaymentWebhookService webhooks;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsReadsListsAndCancelsSubscriptionWithoutStoringPaymentMethod() throws Exception {
        UUID userId = createUser();
        String response = mvc.perform(post("/api/v1/subscriptions").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PREMIUM_MONTHLY\",\"paymentMethodId\":\"token-only\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        UUID subscriptionId = UUID.fromString(mapper.readTree(response).at("/data/id").asText());

        mvc.perform(get("/api/v1/subscriptions/me").with(user(userId.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(subscriptionId.toString()));
        mvc.perform(get("/api/v1/subscriptions/payments").with(user(userId.toString())))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
        Integer leaked = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'PAYMENTS' AND COLUMN_NAME LIKE '%METHOD%'
                """, Integer.class);
        assertThat(leaked).isZero();

        mvc.perform(post("/api/v1/subscriptions/{id}/cancel", subscriptionId).with(user(userId.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("EXPIRED"));
    }

    @Test
    void verifiesMobileReceiptIdempotently() throws Exception {
        UUID userId = createUser();
        String body = "{\"provider\":\"APP_STORE\",\"receipt\":\"monthly-receipt-1\"}";
        mvc.perform(post("/api/v1/subscriptions/mobile/verify").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/subscriptions/mobile/verify").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    @Test
    void webhookChecksSignatureAndProcessesEachEventOnce() throws Exception {
        UUID userId = createUser();
        UUID subscriptionId = createSubscription(userId);
        Instant originalEnd = subscriptions.findById(subscriptionId).orElseThrow().endDate();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "renewal-1");
        event.put("type", "RENEWED");
        event.put("subscriptionId", subscriptionId.toString());
        event.put("occurredAt", "2026-10-06T12:00:00Z");
        String json = mapper.writeValueAsString(event);

        mvc.perform(post("/api/v1/payments/webhooks/MOCK").header("X-Signature", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        String signature = webhooks.signature(event);
        mvc.perform(post("/api/v1/payments/webhooks/MOCK").header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNoContent());
        Instant renewedEnd = subscriptions.findById(subscriptionId).orElseThrow().endDate();
        mvc.perform(post("/api/v1/payments/webhooks/MOCK").header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNoContent());
        assertThat(renewedEnd).isAfter(originalEnd);
        assertThat(subscriptions.findById(subscriptionId).orElseThrow().endDate()).isEqualTo(renewedEnd);

        Map<String, Object> stale = new LinkedHashMap<>();
        stale.put("eventId", "stale-cancel-1");
        stale.put("type", "CANCELLED");
        stale.put("subscriptionId", subscriptionId.toString());
        stale.put("occurredAt", "2026-10-05T12:00:00Z");
        mvc.perform(post("/api/v1/payments/webhooks/MOCK").header("X-Signature", webhooks.signature(stale))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(stale)))
                .andExpect(status().isNoContent());
        assertThat(subscriptions.findById(subscriptionId).orElseThrow().status())
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    private UUID createSubscription(UUID userId) throws Exception {
        String result = mvc.perform(post("/api/v1/subscriptions").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PREMIUM_MONTHLY\",\"paymentMethodId\":\"webhook-token\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(result).at("/data/id").asText());
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

