package com.erudit.payment.controller;

import lombok.RequiredArgsConstructor;
import com.erudit.payment.dto.PaymentPageResponse;
import com.erudit.payment.model.Payment;
import com.erudit.payment.model.Subscription;
import com.erudit.payment.service.PaymentWebhookService;
import com.erudit.payment.service.SubscriptionService;

import com.erudit.openapi.api.PaymentsApi;
import com.erudit.openapi.model.MobileReceiptRequest;
import com.erudit.openapi.model.PageMetadataResponse;
import com.erudit.openapi.model.PaymentListResponse;
import com.erudit.openapi.model.SubscriptionResponse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {
    private final SubscriptionService subscriptions;
    private final PaymentWebhookService webhooks;
    private final HttpServletRequest request;



    @Override
    public ResponseEntity<PaymentListResponse> listPayments(Integer page, Integer size) {
        PaymentPageResponse result = subscriptions.payments(currentUser(), page, size);
        var items = result.items().stream().map(value -> new com.erudit.openapi.model.PaymentDataResponse(
                value.id(), value.status().name()).amount(value.amount().doubleValue())
                .currency(value.currency().getCurrencyCode())
                .paidAt(value.paidAt() == null ? null : value.paidAt().atOffset(ZoneOffset.UTC))).toList();
        var metadata = new PageMetadataResponse(result.page(), result.size(), result.total(),
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
        return com.erudit.user.config.LabOneRequestIdentity.userId(request);
    }
}

