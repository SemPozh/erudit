package com.erudit.payment;
import static org.assertj.core.api.Assertions.*;
import com.erudit.payment.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class MockProviderVerifierTest {
    private final Instant now=Instant.parse("2026-11-04T10:00:00Z");
    private final MockReceiptVerifier receipts=new MockReceiptVerifier(Clock.fixed(now,ZoneOffset.UTC));
    private final MockWebhookVerifier signatures=new MockWebhookVerifier("local-webhook-secret");
    @Test void localReceiptIdentityIsStableAndValidationDoesNotUseNetwork() {
        var first=receipts.verify("APP_STORE","yearly-receipt");
        assertThat(first).isEqualTo(receipts.verify("app_store","yearly-receipt"));
        assertThat(first.planCode()).isEqualTo("PREMIUM_YEARLY");
        assertThat(first.expiresAt()).isEqualTo(now.plus(Duration.ofDays(365)));
        assertThat(receipts.verify("GOOGLE_PLAY","monthly").expiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
        for(String bad:Arrays.asList(null,"","invalid-demo","x".repeat(4097)))
            assertThatThrownBy(() -> receipts.verify("APP_STORE",bad)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> receipts.verify(null,"receipt")).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> receipts.verify("REAL_PROVIDER","receipt")).isInstanceOf(PaymentException.class);
    }
    @Test void signaturesAreIndependentOfMapOrderAndRejectTamperingAndInvalidFields() {
        Map<String,Object> event=new LinkedHashMap<>();
        event.put("type","RENEWED");event.put("subscriptionId",UUID.randomUUID().toString());
        event.put("occurredAt",now.toString());event.put("eventId","unit-87");
        var sorted=new TreeMap<>(event);String signature=signatures.signature(event);
        assertThat(signature).isEqualTo(signatures.signature(sorted));
        assertThat(signatures.verify("MOCK",signature.toUpperCase(Locale.ROOT),event).type()).isEqualTo("RENEWED");
        assertThatThrownBy(() -> signatures.verify("REAL",signature,event)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> signatures.verify("MOCK",null,event)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> signatures.verify("MOCK",signature,null)).isInstanceOf(PaymentException.class);
        event.put("eventId","tampered");
        assertThatThrownBy(() -> signatures.verify("MOCK",signature,event)).isInstanceOf(PaymentException.class);
        for (var invalid:List.of(Map.entry("type","UNKNOWN"),Map.entry("subscriptionId","bad"),Map.entry("occurredAt","bad"),Map.entry("eventId"," "))) {
            var bad=new HashMap<>(sorted);bad.put(invalid.getKey(),invalid.getValue());
            assertThatThrownBy(() -> signatures.verify("MOCK",signatures.signature(bad),bad)).isInstanceOf(PaymentException.class);
        }
        var bad=new HashMap<>(sorted);bad.remove("eventId");
        assertThatThrownBy(() -> signatures.verify("MOCK",signatures.signature(bad),bad)).isInstanceOf(PaymentException.class);
    }
}
