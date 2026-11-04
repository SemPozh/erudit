package com.erudit.payment.model;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
@Table("payments")
public record Payment(@Id UUID id, UUID userId, UUID subscriptionId, java.math.BigDecimal amount,
        String currency, String status, String provider, String externalId, Instant createdAt, Instant paidAt) {}
