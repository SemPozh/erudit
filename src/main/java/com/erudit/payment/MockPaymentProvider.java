package com.erudit.payment;

import com.erudit.web.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Component
public class MockPaymentProvider implements PaymentProvider {
    @Override public String name() { return "MOCK"; }

    @Override
    public PaymentResult charge(UUID userId, String paymentMethodId, BigDecimal amount, Currency currency) {
        if (paymentMethodId == null || paymentMethodId.isBlank()) {
            throw new ValidationException("paymentMethodId is required");
        }
        if (paymentMethodId.startsWith("fail_")) {
            return new PaymentResult("mock_" + UUID.randomUUID(), PaymentStatus.FAILED);
        }
        return new PaymentResult("mock_" + UUID.randomUUID(), PaymentStatus.PAID);
    }
}

