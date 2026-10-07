package com.erudit.payment;

import com.erudit.openapi.api.PaymentsApi;
import com.erudit.openapi.model.MobileReceiptRequest;
import com.erudit.openapi.model.PageMetadata;
import com.erudit.openapi.model.PaymentListResponse;
import com.erudit.openapi.model.SubscriptionResponse;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@RestController
public class PaymentController implements PaymentsApi {
    private final SubscriptionService subscriptions;
    private final PaymentWebhookService webhooks;
    private final HttpServletRequest request;

    public PaymentController(SubscriptionService subscriptions, PaymentWebhookService webhooks,
                             HttpServletRequest request) {
        this.subscriptions = subscriptions;
        this.webhooks = webhooks;
        this.request = request;
    }

    @Override
    public ResponseEntity<PaymentListResponse> listPayments(Integer page, Integer size) {
        PaymentPage result = subscriptions.payments(currentUser(), page, size);
        var items = result.items().stream().map(value -> new com.erudit.openapi.model.Payment(
                value.id(), value.status().name()).amount(value.amount().doubleValue())
                .currency(value.currency().getCurrencyCode())
                .paidAt(value.paidAt() == null ? null : value.paidAt().atOffset(ZoneOffset.UTC))).toList();
        var metadata = new PageMetadata(result.page(), result.size(), result.total(),
                (int) Math.ceil((double) result.total() / result.size()));
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new PaymentListResponse(items).pagination(metadata));
    }

    @Override
    public ResponseEntity<SubscriptionResponse> verifyMobileReceipt(MobileReceiptRequest body) {
        Subscription subscription = subscriptions.verifyMobile(currentUser(), body.getProvider().getValue(), body.getReceipt());
        return ResponseEntity.ok(SubscriptionController.response(subscription));
    }

    @Override
    public ResponseEntity<Void> paymentWebhook(String provider, String signature, Map<String, Object> body) {
        webhooks.accept(provider, signature, body);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUser() {
        if (request.getUserPrincipal() == null) throw new UnauthorizedException("Authentication is required");
        try {
            return UUID.fromString(request.getUserPrincipal().getName());
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Authenticated user id is invalid");
        }
    }
}

