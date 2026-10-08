package com.erudit.payment.service;

import com.erudit.payment.dto.PaymentPage;
import com.erudit.payment.model.BillingPeriod;
import com.erudit.payment.model.Payment;
import com.erudit.payment.model.PaymentStatus;
import com.erudit.payment.model.Subscription;
import com.erudit.payment.model.SubscriptionPlan;
import com.erudit.payment.model.SubscriptionStatus;
import com.erudit.payment.repository.PaymentRepository;
import com.erudit.payment.repository.SubscriptionPlanRepository;
import com.erudit.payment.repository.SubscriptionRepository;

import com.erudit.web.exception.ConflictException;
import com.erudit.web.exception.ForbiddenException;
import com.erudit.web.exception.NotFoundException;
import com.erudit.web.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SubscriptionService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final PaymentRepository payments;
    private final PaymentProvider paymentProvider;
    private final List<MobileReceiptVerifier> receiptVerifiers;
    private final PremiumAccessService premiumAccess;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, SubscriptionPlanRepository plans,
                               PaymentRepository payments, PaymentProvider paymentProvider,
                               List<MobileReceiptVerifier> receiptVerifiers,
                               PremiumAccessService premiumAccess, Clock clock) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.payments = payments;
        this.paymentProvider = paymentProvider;
        this.receiptVerifiers = receiptVerifiers;
        this.premiumAccess = premiumAccess;
        this.clock = clock;
    }

    @Transactional
    public Subscription create(UUID userId, String planCode, String paymentMethodId) {
        if (premiumAccess.isPremiumActive(userId)) throw new ConflictException("User already has an active subscription");
        SubscriptionPlan plan = requirePlan(planCode);
        PaymentProvider.PaymentResult result = paymentProvider.charge(userId, paymentMethodId,
                plan.price(), plan.currency());
        Instant now = clock.instant();
        UUID subscriptionId = UUID.randomUUID();
        if (result.status() != PaymentStatus.PAID) {
            payments.save(new Payment(UUID.randomUUID(), userId, null, plan.price(), plan.currency(),
                    result.status(), paymentProvider.name(), result.externalId(), now, null));
            throw new ConflictException("Payment was not completed");
        }
        Subscription subscription = new Subscription(subscriptionId, userId, plan.code(), SubscriptionStatus.ACTIVE,
                now, endDate(now, plan.period()));
        subscriptions.save(subscription);
        payments.save(new Payment(UUID.randomUUID(), userId, subscription.id(), plan.price(), plan.currency(),
                PaymentStatus.PAID, paymentProvider.name(), result.externalId(), now, now));
        return subscription;
    }

    @Transactional
    public Subscription cancel(UUID userId, UUID subscriptionId) {
        Subscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
        if (!subscription.userId().equals(userId)) throw new ForbiddenException("Subscription belongs to another user");
        if (subscription.status() != SubscriptionStatus.ACTIVE) return subscription;
        subscriptions.updateStatus(subscription.id(), SubscriptionStatus.EXPIRED);
        return subscription.expire();
    }

    @Transactional
    public Subscription current(UUID userId) {
        premiumAccess.expireIfNecessary(userId);
        return subscriptions.findLatestByUser(userId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
    }

    public PaymentPage payments(UUID userId, int page, int size) {
        return payments.findByUser(userId, page, size);
    }

    @Transactional
    public Subscription verifyMobile(UUID userId, String provider, String receipt) {
        MobileReceiptVerifier verifier = receiptVerifiers.stream()
                .filter(candidate -> candidate.provider().equalsIgnoreCase(provider))
                .findFirst().orElseThrow(() -> new ValidationException("Unsupported mobile provider"));
        MobileReceiptVerifier.VerifiedReceipt verified = verifier.verify(receipt);
        SubscriptionPlan plan = requirePlan(verified.planCode());
        Subscription subscription = subscriptions.findLatestByUser(userId)
                .filter(existing -> existing.status() == SubscriptionStatus.ACTIVE)
                .map(existing -> new Subscription(existing.id(), userId, plan.code(), SubscriptionStatus.ACTIVE,
                        existing.startDate(), verified.expiresAt()))
                .orElseGet(() -> new Subscription(UUID.randomUUID(), userId, plan.code(), SubscriptionStatus.ACTIVE,
                        verified.purchasedAt(), verified.expiresAt()));
        subscriptions.upsert(subscription);
        try {
            payments.save(new Payment(UUID.randomUUID(), userId, subscription.id(), plan.price(), plan.currency(),
                    PaymentStatus.PAID, verifier.provider(), verified.externalId(), verified.purchasedAt(), verified.purchasedAt()));
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // Store receipts are idempotent by (provider, external_id).
        }
        return subscription;
    }

    private SubscriptionPlan requirePlan(String code) {
        if (code == null || code.isBlank()) throw new ValidationException("plan is required");
        return plans.findAvailableByCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ValidationException("Unknown or unavailable subscription plan"));
    }

    static Instant endDate(Instant start, BillingPeriod period) {
        return start.plus(period == BillingPeriod.YEAR ? 365 : 30, ChronoUnit.DAYS);
    }
}

