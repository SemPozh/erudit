package com.erudit.payment.service;

import com.erudit.payment.model.Payment;
import com.erudit.payment.model.Subscription;
import com.erudit.payment.repository.PaymentRepository;
import com.erudit.payment.repository.ProviderEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProviderPaymentService {
    private final PaymentRepository payments;
    private final ProviderEventRepository events;
    private final SubscriptionService subscriptions;
    private final MockReceiptVerifier receipts;
    private final MockWebhookVerifier webhooks;
    private final TransactionTemplate transactions;
    private final Clock clock;
    public ProviderPaymentService(PaymentRepository payments, ProviderEventRepository events, SubscriptionService subscriptions,
            MockReceiptVerifier receipts, MockWebhookVerifier webhooks, TransactionTemplate transactions, Clock clock) {
        this.payments=payments; this.events=events; this.subscriptions=subscriptions; this.receipts=receipts;
        this.webhooks=webhooks; this.transactions=transactions; this.clock=clock;
    }
    public Subscription verifyReceipt(UUID user, String provider, String rawReceipt) {
        var receipt = receipts.verify(provider,rawReceipt);
        subscriptions.requireUser(user);
        // Receipt ownership, subscription and payment are committed atomically, under the same owner lock as purchases.
        return transactions.execute(tx -> {
            payments.lockOwner(user);
            var previous = events.receipt(receipt.provider(),receipt.receiptId());
            if (previous.isPresent()) {
                if (!previous.get().userId().equals(user)) throw new PaymentException(409,"Receipt belongs to another user");
                return payments.find(previous.get().subscriptionId()).orElseThrow();
            }
            var plan = payments.plan(receipt.planCode()).orElseThrow(() -> new PaymentException(400,"Unavailable receipt plan"));
            payments.expire(user,clock.instant());
            var current = payments.latest(user).filter(s -> s.activeAt(clock.instant()));
            Subscription subscription;
            if (current.isPresent()) {
                var old = current.get();
                Instant end = old.endDate().isAfter(receipt.expiresAt()) ? old.endDate() : receipt.expiresAt();
                subscription = payments.replace(new Subscription(old.id(),user,plan.code(),"ACTIVE",old.startDate(),end));
            } else {
                subscription = payments.save(new Subscription(UUID.randomUUID(),user,plan.code(),"ACTIVE",receipt.purchasedAt(),receipt.expiresAt()));
            }
            events.saveReceipt(receipt.provider(),receipt.receiptId(),user,subscription.id());
            events.recordReceiptTime(subscription.id(),receipt.purchasedAt());
            payments.save(new Payment(UUID.randomUUID(),user,subscription.id(),plan.price(),plan.currency(),"PAID",
                    receipt.provider(),receipt.provider().toLowerCase(java.util.Locale.ROOT)+"_"+receipt.receiptId(),clock.instant(),clock.instant()));
            return subscription;
        });
    }
    public void webhook(String provider, String signature, Map<String,Object> body) {
        var event = webhooks.verify(provider,signature,body);
        transactions.executeWithoutResult(tx -> {
            var first = payments.find(event.subscriptionId()).orElseThrow(() -> new PaymentException(404,"Subscription not found"));
            payments.lockOwner(first.userId());
            var subscription = payments.find(first.id()).orElseThrow();
            if (!events.claim(event,clock.instant())) return;
            // An event for an older subscription cannot resurrect it over a newer purchase.
            if (!payments.latest(subscription.userId()).orElseThrow().id().equals(subscription.id())) return;
            if (!event.type().equals("RENEWED")) {
                events.apply(event,subscription.endDate());
                return;
            }
            var plan = payments.historicalPlan(subscription.planCode()).orElseThrow();
            Instant base = subscription.endDate().isAfter(clock.instant()) ? subscription.endDate() : clock.instant();
            Instant end = base.plus(plan.period().equals("YEAR") ? 365 : 30,ChronoUnit.DAYS);
            if (events.apply(event,end))
                payments.save(new Payment(UUID.randomUUID(),subscription.userId(),subscription.id(),plan.price(),plan.currency(),"PAID",
                        event.provider(),event.provider().toLowerCase(java.util.Locale.ROOT)+"_"+event.id(),clock.instant(),clock.instant()));
        });
    }
}
