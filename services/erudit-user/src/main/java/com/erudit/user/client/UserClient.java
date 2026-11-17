package com.erudit.user.client;

import com.erudit.integration.InternalContracts.UserLookup;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "erudit-user", contextId = "internalUserClient")
public interface UserClient {
    @GetMapping("/internal/v1/users/{id}")
    UserLookup get(@PathVariable("id") UUID id);
}