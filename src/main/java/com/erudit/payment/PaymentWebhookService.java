package com.erudit.payment;

import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentWebhookService {
    private static final Set<String> PROVIDERS = Set.of("MOCK", "APP_STORE", "GOOGLE_PLAY");
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final PaymentRepository payments;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock;
    private final String secret;

    public PaymentWebhookService(SubscriptionRepository subscriptions, SubscriptionPlanRepository plans,
                                 PaymentRepository payments, Clock clock,
                                 @Value("${erudit.payments.webhook-secret:local-webhook-secret}") String secret) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.payments = payments;
        this.clock = clock;
        this.secret = secret;
    }

    @Transactional
    public void accept(String rawProvider, String signature, Map<String, Object> body) {
        String provider = rawProvider == null ? "" : rawProvider.toUpperCase(Locale.ROOT);
        if (!PROVIDERS.contains(provider)) throw new ValidationException("Unsupported payment provider");
        verifySignature(signature, body);
        String eventId = required(body, "eventId");
        String type = required(body, "type").toUpperCase(Locale.ROOT);
        Instant occurredAt = instant(body, "occurredAt");
        UUID subscriptionId = uuid(body, "subscriptionId");
        Subscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
        if (!payments.registerEvent(provider, eventId, clock.instant())) return;

        switch (type) {
            case "RENEWED" -> renew(provider, eventId, subscription, occurredAt);
            case "EXPIRED", "CANCELLED" -> subscriptions.applyStatusEvent(
                    subscription.id(), SubscriptionStatus.EXPIRED, occurredAt);
            default -> throw new ValidationException("Unsupported payment event type");
        }
    }

    public String signature(Map<String, Object> body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(body)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot calculate webhook signature", exception);
        }
    }

    private void verifySignature(String actual, Map<String, Object> body) {
        String expected = signature(body);
        if (actual == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new ValidationException("Invalid webhook signature");
        }
    }

    private void renew(String provider, String eventId, Subscription subscription, Instant occurredAt) {
        SubscriptionPlan plan = plans.findByCode(subscription.planCode())
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));
        Instant base = subscription.endDate().isAfter(clock.instant()) ? subscription.endDate() : clock.instant();
        if (!subscriptions.applyRenewalEvent(subscription.id(),
                SubscriptionService.endDate(base, plan.period()), occurredAt)) return;
        payments.save(new Payment(UUID.randomUUID(), subscription.userId(), subscription.id(),
                plan.price(), plan.currency(), PaymentStatus.PAID, provider,
                provider.toLowerCase(Locale.ROOT) + "_" + eventId, clock.instant(), clock.instant()));
    }

    private byte[] canonical(Map<String, Object> body) throws JsonProcessingException {
        return objectMapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsBytes(body);
    }

    private static String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) throw new ValidationException(key + " is required");
        return value.toString();
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        try {
            return UUID.fromString(required(body, key));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(key + " must be a UUID");
        }
    }

    private static Instant instant(Map<String, Object> body, String key) {
        try {
            return Instant.parse(required(body, key));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ValidationException(key + " must be an ISO-8601 instant");
        }
    }
}

