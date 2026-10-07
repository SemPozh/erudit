package com.erudit.payment;

import com.erudit.openapi.api.SubscriptionsApi;
import com.erudit.openapi.model.SubscriptionCreateRequest;
import com.erudit.openapi.model.SubscriptionPlanListResponse;
import com.erudit.openapi.model.SubscriptionResponse;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class SubscriptionController implements SubscriptionsApi {
    private final SubscriptionPlanCatalogService planCatalog;
    private final SubscriptionService subscriptions;
    private final HttpServletRequest request;

    public SubscriptionController(SubscriptionPlanCatalogService planCatalog,
                                  SubscriptionService subscriptions, HttpServletRequest request) {
        this.planCatalog = planCatalog;
        this.subscriptions = subscriptions;
        this.request = request;
    }

    @Override
    public ResponseEntity<SubscriptionPlanListResponse> listSubscriptionPlans() {
        var plans = planCatalog.availablePlans().stream().map(plan ->
                new com.erudit.openapi.model.SubscriptionPlan(
                        plan.code(), plan.name(), plan.price().toPlainString(),
                        plan.currency().getCurrencyCode(), plan.period().name()))
                .toList();
        return ResponseEntity.ok(new SubscriptionPlanListResponse(plans));
    }

    @Override public ResponseEntity<SubscriptionResponse> createSubscription(SubscriptionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(response(
                subscriptions.create(currentUser(), request.getPlan(), request.getPaymentMethodId())));
    }
    @Override public ResponseEntity<SubscriptionResponse> getMySubscription() {
        return ResponseEntity.ok(response(subscriptions.current(currentUser())));
    }
    @Override public ResponseEntity<SubscriptionResponse> cancelSubscription(UUID id) {
        return ResponseEntity.ok(response(subscriptions.cancel(currentUser(), id)));
    }

    static SubscriptionResponse response(Subscription value) {
        var data = new com.erudit.openapi.model.Subscription(value.id(), value.status().name())
                .plan(value.planCode()).expiresAt(value.endDate().atOffset(java.time.ZoneOffset.UTC));
        return new SubscriptionResponse(data);
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
