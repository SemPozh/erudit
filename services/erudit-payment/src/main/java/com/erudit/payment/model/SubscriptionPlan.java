package com.erudit.payment.model;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
@Table("subscription_plans")
public record SubscriptionPlan(@Id String code, String name, java.math.BigDecimal price,
        String currency, String period, boolean available) {}
