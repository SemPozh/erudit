package com.erudit.payment.client;

import com.erudit.integration.InternalContracts.Entitlement;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "erudit-payment", contextId = "internalEntitlementClient")
public interface EntitlementClient {
    @GetMapping("/internal/v1/entitlements/{userId}")
    Entitlement get(@PathVariable("userId") UUID userId);
}