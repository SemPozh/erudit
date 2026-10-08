package com.erudit.payment.service;

import com.erudit.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

public interface PaymentProvider {
    String name();

    PaymentResult charge(UUID userId, String paymentMethodId, BigDecimal amount, Currency currency);

    record PaymentResult(String externalId, PaymentStatus status) {
    }
}

