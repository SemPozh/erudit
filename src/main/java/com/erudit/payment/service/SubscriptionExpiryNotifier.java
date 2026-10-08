package com.erudit.payment.service;

import com.erudit.payment.model.Subscription;

public interface SubscriptionExpiryNotifier {
    void notifyExpiring(Subscription subscription);
}

