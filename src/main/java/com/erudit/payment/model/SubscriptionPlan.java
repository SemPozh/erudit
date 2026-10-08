package com.erudit.payment.model;

import java.math.BigDecimal;
import java.util.Currency;

public record SubscriptionPlan(String code, String name, BigDecimal price,
                               Currency currency, BillingPeriod period) {
    public SubscriptionPlan {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Plan code is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Plan name is required");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("Plan price cannot be negative");
        if (currency == null) throw new IllegalArgumentException("Plan currency is required");
        if (period == null) throw new IllegalArgumentException("Billing period is required");
    }
}
