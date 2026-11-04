package com.erudit.admin.client;
import com.erudit.admin.openapi.model.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
public final class RemoteClients {
    private RemoteClients(){}
    @FeignClient(name="erudit-user",contextId="adminUser",url="${erudit.admin.clients.user-url:}")
    public interface Users {
        @GetMapping("/internal/v1/admin/users") UserPage search(@RequestParam("query")String query,@RequestParam("page")int page,@RequestParam("size")int size,@RequestHeader("X-Request-ID")String requestId);
        @PostMapping("/internal/v1/admin/users/{id}/block") void block(@PathVariable("id")UUID id,@RequestHeader("X-Request-ID")String requestId);
        @GetMapping("/internal/v1/users/{id}") UserState get(@PathVariable("id")UUID id,@RequestHeader("X-Request-ID")String requestId);
    }
    @FeignClient(name="erudit-content",contextId="adminContent",url="${erudit.admin.clients.content-url:}")
    public interface Content {
        @GetMapping("/internal/v1/content/moderation") ResponseEntity<ContentListResponse> queue(@RequestParam("page")int page,@RequestParam("size")int size,@RequestHeader("X-Request-ID")String requestId);
        @PostMapping("/internal/v1/moderation") Moderated moderate(@RequestBody Moderation request,@RequestHeader("X-User-Id")UUID actor,@RequestHeader("X-Request-ID")String requestId);
        @GetMapping("/internal/v1/content/reports") ResponseEntity<ContentReportListResponse> reports(@RequestParam("page")int page,@RequestParam("size")int size,@RequestHeader("X-Request-ID")String requestId);
        @PostMapping("/internal/v1/content/reports/{id}/resolve") void resolve(@PathVariable("id")UUID id,@RequestBody ReportResolutionRequest request,@RequestHeader("X-User-Id")UUID actor,@RequestHeader("X-Request-ID")String requestId);
    }
    @FeignClient(name="erudit-notificator",contextId="adminNotification",url="${erudit.admin.clients.notification-url:}")
    public interface Notifications {
        @GetMapping("/api/v1/notifications/templates/{id}") Template template(@PathVariable("id")UUID id,@RequestHeader("X-Request-ID")String requestId);
        @PostMapping("/internal/v1/notifications/enqueue") NotificationAck enqueue(@RequestBody Enqueue request,@RequestHeader("X-Request-ID")String requestId);
    }
    @FeignClient(name="erudit-payment",contextId="adminPayment",url="${erudit.admin.clients.payment-url:}")
    public interface Payments {
        @GetMapping("/api/v1/subscriptions/me") SubscriptionResponse subscription(@RequestHeader("X-User-Id")UUID user,@RequestHeader("X-Request-ID")String requestId);
        @GetMapping("/api/v1/subscriptions/payments") ResponseEntity<PaymentListResponse> payments(@RequestParam("page")int page,@RequestParam("size")int size,@RequestHeader("X-User-Id")UUID user,@RequestHeader("X-Request-ID")String requestId);
    }
    public record UserPage(List<UserProfileDataResponse> data,int page,int size,long total){}
    public record UserState(UUID id,String displayName,boolean active){}
    public record Moderation(UUID contentId,String status,String reason){}
    public record Moderated(UUID contentId,String status,String reason,ContentItemDataResponse data){}
    public record Template(UUID id,String code,String titleTemplate,String bodyTemplate,boolean enabled){}
    public record Enqueue(UUID userId,String templateCode,String title,String body){}
    public record NotificationAck(UUID id,UUID userId){}
}
