package com.erudit.payment.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockReceiptVerifier {
    private final Clock clock;
    public MockReceiptVerifier(Clock clock) { this.clock = clock; }
    public VerifiedReceipt verify(String rawProvider, String receipt) {
        String provider = rawProvider == null ? "" : rawProvider.toUpperCase(Locale.ROOT);
        if (!provider.equals("APP_STORE") && !provider.equals("GOOGLE_PLAY"))
            throw new PaymentException(400, "Unsupported mobile provider");
        if (receipt == null || receipt.isBlank() || receipt.length() > 4096
                || receipt.toLowerCase(Locale.ROOT).startsWith("invalid"))
            throw new PaymentException(400, "Mobile receipt is invalid");
        boolean yearly = receipt.toLowerCase(Locale.ROOT).contains("year");
        Instant now = clock.instant();
        return new VerifiedReceipt(provider, UUID.nameUUIDFromBytes(receipt.getBytes(StandardCharsets.UTF_8)).toString(),
                yearly ? "PREMIUM_YEARLY" : "PREMIUM_MONTHLY", now, now.plus(yearly ? 365 : 30, ChronoUnit.DAYS));
    }
    public record VerifiedReceipt(String provider, String receiptId, String planCode, Instant purchasedAt, Instant expiresAt) {}
}
