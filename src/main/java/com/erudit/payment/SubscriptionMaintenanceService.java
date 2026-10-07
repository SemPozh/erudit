package com.erudit.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Service
public class SubscriptionMaintenanceService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionExpiryNotifier notifier;
    private final Clock clock;

    public SubscriptionMaintenanceService(SubscriptionRepository subscriptions,
                                          SubscriptionExpiryNotifier notifier, Clock clock) {
        this.subscriptions = subscriptions;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${erudit.payments.maintenance-interval:PT1H}",
            initialDelayString = "${erudit.payments.maintenance-initial-delay:PT1M}")
    @Transactional
    public void maintain() {
        var now = clock.instant();
        subscriptions.expireEnded(now);
        subscriptions.findExpiringBetween(now, now.plus(3, ChronoUnit.DAYS)).forEach(subscription -> {
            if (subscriptions.claimExpiryNotification(subscription.id(), now)) {
                notifier.notifyExpiring(subscription);
            }
        });
    }
}

