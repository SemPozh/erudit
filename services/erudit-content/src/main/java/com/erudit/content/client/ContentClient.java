package com.erudit.content.client;

import com.erudit.integration.InternalContracts.ContentLookup;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "erudit-content", contextId = "internalContentClient")
public interface ContentClient {
    @GetMapping("/internal/v1/content/{id}")
    ContentLookup get(@PathVariable("id") UUID id);
}