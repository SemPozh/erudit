package com.erudit.payment.controller;

import com.erudit.payment.model.Subscription;
import com.erudit.payment.openapi.api.*;
import com.erudit.payment.openapi.model.*;
import com.erudit.payment.service.SubscriptionService;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubscriptionController implements SubscriptionsApi, PaymentsApi, EntitlementsApi {
    private static final UUID DEMO_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final SubscriptionService service;
    public SubscriptionController(SubscriptionService service) { this.service = service; }
    private UUID user(UUID value) { return value == null ? DEMO_USER : value; }
    @Override public ResponseEntity<SubscriptionPlanListResponse> listSubscriptionPlans() {
        return ResponseEntity.ok(new SubscriptionPlanListResponse(service.plans().stream().map(p ->
                new SubscriptionPlanDataResponse(p.code(), p.name(), p.price().toPlainString(), p.currency(), p.period())).toList()));
    }
    @Override public ResponseEntity<SubscriptionResponse> createSubscription(SubscriptionCreateRequest body, UUID xUserId) {
        return ResponseEntity.status(201).body(response(service.create(user(xUserId), body.getPlan(), body.getPaymentMethodId())));
    }
    @Override public ResponseEntity<SubscriptionResponse> getMySubscription(UUID xUserId) {
        return ResponseEntity.ok(response(service.current(user(xUserId))));
    }
    @Override public ResponseEntity<SubscriptionResponse> cancelSubscription(UUID id, UUID xUserId) {
        return ResponseEntity.ok(response(service.cancel(user(xUserId), id)));
    }
    @Override public ResponseEntity<PaymentListResponse> listPayments(Integer page, Integer size, UUID xUserId) {
        var result = service.payments(user(xUserId), page, size);
        var items = result.items().stream().map(p -> new PaymentDataResponse(p.id(), p.status())
                .amount(p.amount().doubleValue()).currency(p.currency())
                .paidAt(p.paidAt() == null ? null : p.paidAt().atOffset(ZoneOffset.UTC))).toList();
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new PaymentListResponse(items).pagination(new PageMetadataResponse(page, size, result.total(),
                        (int) ((result.total() + size - 1) / size))));
    }
    @Override public ResponseEntity<EntitlementResponse> getEntitlement(UUID userId) {
        var result = service.entitlement(userId);
        return ResponseEntity.ok(new EntitlementResponse(result.userId(), result.premium())
                .expiresAt(result.expiresAt() == null ? null : result.expiresAt().atOffset(ZoneOffset.UTC)));
    }
    private SubscriptionResponse response(Subscription s) {
        return new SubscriptionResponse(new SubscriptionDataResponse(s.id(), s.status())
                .plan(s.planCode()).expiresAt(s.endDate().atOffset(ZoneOffset.UTC)));
    }
}
