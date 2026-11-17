package com.erudit.content.client;

import com.erudit.integration.InternalContracts.Moderation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "erudit-content", contextId = "internalModerationClient")
public interface ModerationClient {
    @PostMapping("/internal/v1/moderation")
    Moderation moderate(@RequestBody Moderation request);
}