package com.erudit.notificator.client;

import com.erudit.integration.InternalContracts.Notification;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "erudit-notificator", contextId = "internalNotificationClient")
public interface NotificationClient {
    @PostMapping("/internal/v1/notifications")
    void enqueue(@RequestBody Notification request);
}