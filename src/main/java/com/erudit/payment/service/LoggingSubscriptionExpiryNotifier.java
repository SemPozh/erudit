package com.erudit.payment.service;

import com.erudit.payment.model.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingSubscriptionExpiryNotifier implements SubscriptionExpiryNotifier {
    private static final Logger log = LoggerFactory.getLogger(LoggingSubscriptionExpiryNotifier.class);

    @Override
    public void notifyExpiring(Subscription subscription) {
        // This boundary is replaced by erudit-notificator after service decomposition.
        log.info("Subscription {} for user {} expires at {}",
                subscription.id(), subscription.userId(), subscription.endDate());
    }
}

