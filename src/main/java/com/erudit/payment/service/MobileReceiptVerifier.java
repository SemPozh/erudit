package com.erudit.payment.service;

import java.time.Instant;

public interface MobileReceiptVerifier {
    String provider();

    VerifiedReceipt verify(String receipt);

    record VerifiedReceipt(String externalId, String planCode, Instant purchasedAt, Instant expiresAt) {
    }
}

