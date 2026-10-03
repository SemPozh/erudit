package com.erudit.payment;

import com.erudit.openapi.api.SubscriptionsApi;
import com.erudit.openapi.model.SubscriptionCreateRequest;
import com.erudit.openapi.model.SubscriptionPlanListResponse;
import com.erudit.openapi.model.SubscriptionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class SubscriptionController implements SubscriptionsApi {
    private final SubscriptionPlanCatalogService planCatalog;

    public SubscriptionController(SubscriptionPlanCatalogService planCatalog) {
        this.planCatalog = planCatalog;
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

    // Routes below belong to later tasks sharing the Subscriptions tag.
    @Override public ResponseEntity<SubscriptionResponse> createSubscription(SubscriptionCreateRequest request) {
        return ResponseEntity.notFound().build();
    }
    @Override public ResponseEntity<SubscriptionResponse> getMySubscription() {
        return ResponseEntity.notFound().build();
    }
    @Override public ResponseEntity<SubscriptionResponse> cancelSubscription(UUID id) {
        return ResponseEntity.notFound().build();
    }
}
