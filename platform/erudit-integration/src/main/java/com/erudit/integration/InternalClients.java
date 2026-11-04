package com.erudit.integration;

import com.erudit.integration.InternalContracts.*;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public final class InternalClients {
    private InternalClients() {}
    @FeignClient(name="erudit-user",contextId="internalUserClient") public interface UserClient {@GetMapping("/internal/v1/users/{id}") UserLookup get(@PathVariable("id") UUID id);}
    @FeignClient(name="erudit-content",contextId="internalModerationClient") public interface ModerationClient {@PostMapping("/internal/v1/moderation") Moderation moderate(@RequestBody Moderation request);}
    @FeignClient(name="erudit-notificator",contextId="internalNotificationClient") public interface NotificationClient {@PostMapping("/internal/v1/notifications") void enqueue(@RequestBody Notification request);}
    @FeignClient(name="erudit-payment",contextId="internalEntitlementClient") public interface EntitlementClient {@GetMapping("/internal/v1/entitlements/{userId}") Entitlement get(@PathVariable("userId") UUID userId);}
    @FeignClient(name="erudit-content",contextId="internalContentClient") public interface ContentClient {@GetMapping("/internal/v1/content/{id}") ContentLookup get(@PathVariable("id") UUID id);}
    @FeignClient(name="erudit-event-receiver",contextId="internalAnalyticsClient") public interface AnalyticsClient {@PostMapping("/internal/v1/events") void publish(@RequestBody AnalyticsEvent event);}
    @FeignClient(name="erudit-ai-platform",contextId="internalAiClient") public interface AiClient {@PostMapping("/internal/v1/ai/quiz-generations") AiQuiz quiz(@RequestBody AiRequest request);@PostMapping("/internal/v1/ai/content-cards") AiCard card(@RequestBody AiRequest request);}
}
