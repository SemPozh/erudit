package com.erudit.payment.service;

import com.erudit.payment.model.SubscriptionStatus;
import com.erudit.payment.repository.SubscriptionRepository;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PremiumAccessService {
    private final SubscriptionRepository repository;
    private final Clock clock;

    public PremiumAccessService(SubscriptionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean isPremiumActive(UUID userId) {
        expireIfNecessary(userId);
        return repository.findActiveByUser(userId, clock.instant()).isPresent();
    }

    void expireIfNecessary(UUID userId) {
        Instant now = clock.instant();
        repository.findLatestByUser(userId)
                .filter(value -> value.status() == SubscriptionStatus.ACTIVE && !value.endDate().isAfter(now))
                .ifPresent(value -> repository.updateStatus(value.id(), SubscriptionStatus.EXPIRED));
    }
}

