package com.erudit.payment.service;

import com.erudit.payment.model.SubscriptionPlan;
import com.erudit.payment.repository.SubscriptionPlanRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubscriptionPlanCatalogService {
    private static final int CATALOG_LIMIT = 50;
    private final SubscriptionPlanRepository repository;

    public SubscriptionPlanCatalogService(SubscriptionPlanRepository repository) {
        this.repository = repository;
    }

    public List<SubscriptionPlan> availablePlans() {
        return repository.findAvailable(0, CATALOG_LIMIT);
    }
}
