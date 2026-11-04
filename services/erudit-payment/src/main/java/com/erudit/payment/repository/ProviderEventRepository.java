package com.erudit.payment.repository;

import com.erudit.payment.service.MockWebhookVerifier.Event;
import com.erudit.payment.service.PaymentException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProviderEventRepository {
    private final JdbcClient jdbc;
    public ProviderEventRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    public Optional<Receipt> receipt(String provider, String id) {
        return jdbc.sql("SELECT user_id,subscription_id FROM mobile_receipts WHERE provider=:provider AND receipt_id=:id")
                .param("provider",provider).param("id",id).query(Receipt.class).optional();
    }
    public void saveReceipt(String provider, String id, UUID user, UUID subscription) {
        int saved = jdbc.sql("INSERT INTO mobile_receipts(provider,receipt_id,user_id,subscription_id) VALUES(:provider,:id,:user,:subscription) ON CONFLICT DO NOTHING")
                .param("provider",provider).param("id",id).param("user",user).param("subscription",subscription).update();
        if (saved == 0) throw new PaymentException(409, "Receipt has already been used");
    }
    public boolean claim(Event event, Instant now) {
        int saved = jdbc.sql("INSERT INTO payment_provider_events(provider,event_id,fingerprint,received_at) VALUES(:provider,:id,:fingerprint,:now) ON CONFLICT DO NOTHING")
                .param("provider",event.provider()).param("id",event.id()).param("fingerprint",event.fingerprint())
                .param("now",now.atOffset(ZoneOffset.UTC)).update();
        if (saved > 0) return true;
        String fingerprint = jdbc.sql("SELECT fingerprint FROM payment_provider_events WHERE provider=:provider AND event_id=:id")
                .param("provider",event.provider()).param("id",event.id()).query(String.class).single();
        if (!fingerprint.equals(event.fingerprint())) throw new PaymentException(409, "Event ID was reused with different data");
        return false;
    }
    public void recordReceiptTime(UUID subscription, Instant purchasedAt) {
        jdbc.sql("UPDATE subscriptions SET provider_event_at=GREATEST(COALESCE(provider_event_at,:at),:at) WHERE id=:id")
                .param("at",purchasedAt.atOffset(ZoneOffset.UTC)).param("id",subscription).update();
    }
    public boolean apply(Event event, Instant endDate) {
        return jdbc.sql("UPDATE subscriptions SET status=:status,end_date=:end,provider_event_at=:occurred WHERE id=:id AND start_date<=:occurred AND (provider_event_at IS NULL OR provider_event_at<:occurred)")
                .param("status",event.type().equals("RENEWED") ? "ACTIVE" : "EXPIRED")
                .param("end",endDate.atOffset(ZoneOffset.UTC)).param("occurred",event.occurredAt().atOffset(ZoneOffset.UTC))
                .param("id",event.subscriptionId()).update() > 0;
    }
    public record Receipt(UUID userId, UUID subscriptionId) {}
}
