package com.erudit.payment;

public interface SubscriptionExpiryNotifier {
    void notifyExpiring(Subscription subscription);
}

