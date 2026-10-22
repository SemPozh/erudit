package com.erudit.payment.service;

import lombok.RequiredArgsConstructor;
import com.erudit.web.exception.ValidationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@RequiredArgsConstructor
public class MockMobileReceiptVerifier implements MobileReceiptVerifier {
    private final String provider;



    @Override public String provider() { return provider; }

    @Override
    public VerifiedReceipt verify(String receipt) {
        if (receipt == null || receipt.isBlank() || receipt.toLowerCase(Locale.ROOT).startsWith("invalid")) {
            throw new ValidationException("Mobile receipt is invalid");
        }
        Instant now = Instant.now();
        String plan = receipt.toLowerCase(Locale.ROOT).contains("year")
                ? "PREMIUM_YEARLY" : "PREMIUM_MONTHLY";
        return new VerifiedReceipt(provider.toLowerCase(Locale.ROOT) + "_" + UUID.nameUUIDFromBytes(receipt.getBytes()),
                plan, now, now.plus(plan.endsWith("YEARLY") ? 365 : 30, ChronoUnit.DAYS));
    }
}

