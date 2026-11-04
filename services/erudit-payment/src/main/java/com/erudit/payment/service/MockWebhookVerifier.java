package com.erudit.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MockWebhookVerifier {
    private final String secret;
    private final JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    public MockWebhookVerifier(@Value("${erudit.payments.webhook-secret:local-webhook-secret}") String secret) {
        this.secret = secret;
    }
    public Event verify(String rawProvider, String signature, Map<String,Object> body) {
        String provider = rawProvider == null ? "" : rawProvider.toUpperCase(Locale.ROOT);
        if (!Set.of("MOCK","APP_STORE","GOOGLE_PLAY").contains(provider))
            throw new PaymentException(400, "Unsupported payment provider");
        if (body == null || signature == null || !MessageDigest.isEqual(
                signature(body).getBytes(StandardCharsets.US_ASCII), signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
            throw new PaymentException(400, "Invalid webhook signature");
        try {
            String type = required(body,"type").toUpperCase(Locale.ROOT);
            if (!Set.of("RENEWED","EXPIRED","CANCELLED").contains(type)) throw new IllegalArgumentException();
            String id = required(body,"eventId");
            if (id.length() > 255) throw new IllegalArgumentException();
            return new Event(provider, id, type, UUID.fromString(required(body,"subscriptionId")),
                    Instant.parse(required(body,"occurredAt")), fingerprint(body));
        } catch (IllegalArgumentException | java.time.DateTimeException e) { throw new PaymentException(400, "Invalid webhook event fields"); }
    }
    public String signature(Map<String,Object> body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(mapper.writeValueAsBytes(body)));
        } catch (Exception e) { throw new IllegalStateException("Cannot sign mock webhook", e); }
    }
    private String fingerprint(Map<String,Object> body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body))); }
        catch (Exception e) { throw new IllegalStateException("Cannot fingerprint mock event", e); }
    }
    private String required(Map<String,Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException();
        return text;
    }
    public record Event(String provider, String id, String type, UUID subscriptionId, Instant occurredAt, String fingerprint) {}
}
