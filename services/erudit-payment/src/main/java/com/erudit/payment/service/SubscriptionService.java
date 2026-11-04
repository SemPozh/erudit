package com.erudit.payment.service;

import com.erudit.integration.InternalClients.UserClient;
import com.erudit.payment.model.*;
import com.erudit.payment.repository.PaymentRepository;
import feign.FeignException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SubscriptionService {
    private final PaymentRepository repository;
    private final UserClient users;
    private final Clock clock;
    private final TransactionTemplate transactions;
    public SubscriptionService(PaymentRepository repository, UserClient users, Clock clock, TransactionTemplate transactions) {
        this.repository = repository;
        this.users = users;
        this.clock = clock;
        this.transactions = transactions;
    }
    public List<SubscriptionPlan> plans() { return repository.plans(); }
    public Subscription create(UUID user, String code, String method) {
        if (code == null || code.isBlank() || method == null || method.isBlank())
            throw new PaymentException(400, "plan and paymentMethodId are required");
        requireUser(user);
        // The owner lock serializes concurrent purchases. Payment and activation commit together.
        Subscription result = transactions.execute(tx -> {
            repository.lockOwner(user);
            Instant now = clock.instant();
            repository.expire(user, now);
            if (repository.latest(user).filter(s -> s.activeAt(now)).isPresent())
                throw new PaymentException(409, "User already has an active subscription");
            var plan = repository.plan(code.toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new PaymentException(400, "Unknown or unavailable subscription plan"));
            boolean paid = !method.startsWith("fail_");
            Subscription subscription = paid ? repository.save(new Subscription(UUID.randomUUID(), user,
                    plan.code(), "ACTIVE", now, now.plus(plan.period().equals("YEAR") ? 365 : 30, ChronoUnit.DAYS))) : null;
            repository.save(new Payment(UUID.randomUUID(), user, paid ? subscription.id() : null, plan.price(),
                    plan.currency(), paid ? "PAID" : "FAILED", "MOCK", "mock_" + UUID.randomUUID(), now, paid ? now : null));
            return subscription;
        });
        // A declined mock payment remains visible in history; there is no subscription to activate.
        if (result == null) throw new PaymentException(409, "Payment was not completed");
        return result;
    }
    public Subscription current(UUID user) {
        return transactions.execute(tx -> {
            repository.expire(user, clock.instant());
            return repository.latest(user).orElseThrow(() -> new PaymentException(404, "Subscription not found"));
        });
    }
    public Subscription cancel(UUID user, UUID id) {
        return transactions.execute(tx -> {
            repository.lockOwner(user);
            var subscription = repository.find(id).orElseThrow(() -> new PaymentException(404, "Subscription not found"));
            if (!subscription.userId().equals(user)) throw new PaymentException(403, "Subscription belongs to another user");
            return repository.cancel(id, clock.instant());
        });
    }
    public Entitlement entitlement(UUID user) {
        // Access depends on dates as well as status, even if maintenance has not run yet.
        return repository.latest(user).filter(s -> s.activeAt(clock.instant()))
                .map(s -> new Entitlement(user, true, s.endDate())).orElse(new Entitlement(user, false, null));
    }
    public PaymentPage payments(UUID user, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new PaymentException(400, "Invalid page or size");
        return new PaymentPage(repository.payments(user, page, size), repository.paymentCount(user), page, size);
    }
    void requireUser(UUID user) {
        try {
            var result = users.get(user);
            if (result == null || !user.equals(result.id()) || !result.active())
                throw new PaymentException(404, "User not found or inactive");
        } catch (PaymentException e) {
            throw e;
        } catch (RuntimeException e) {
            // Feign circuit breakers can wrap HTTP errors in a fallback exception.
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof FeignException.NotFound) throw new PaymentException(404, "User not found");
            }
            throw new PaymentException(503, "User service is unavailable");
        }
    }
    public record Entitlement(UUID userId, boolean premium, Instant expiresAt) {}
    public record PaymentPage(List<Payment> items, long total, int page, int size) {}
}
