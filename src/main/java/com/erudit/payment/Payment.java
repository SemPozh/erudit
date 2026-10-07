package com.erudit.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record Payment(UUID id, UUID userId, UUID subscriptionId, BigDecimal amount,
                      Currency currency, PaymentStatus status, String provider,
                      String externalId, Instant createdAt, Instant paidAt) {
    public Payment {
        if (id == null || userId == null) throw new IllegalArgumentException("Payment identity is required");
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Payment amount is invalid");
        if (currency == null || status == null) throw new IllegalArgumentException("Payment currency and status are required");
        if (provider == null || provider.isBlank() || externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("Payment provider identity is required");
        }
        if (createdAt == null) throw new IllegalArgumentException("Payment creation time is required");
    }
}

