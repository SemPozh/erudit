package com.erudit.admin.application.port;

import com.erudit.admin.application.AdminModels.*;
import java.util.List;
import java.util.UUID;

public final class AdminRemote {
    private AdminRemote() {}
    public interface Users {
        UserPage search(String query, int page, int size, String requestId);
        void block(UUID id, String requestId);
        UserState get(UUID id, String requestId);
    }
    public interface Content {
        Page<ContentItem> queue(int page, int size, String requestId);
        Moderated moderate(Moderation request, UUID actor, String requestId);
        Page<ContentReport> reports(int page, int size, String requestId);
        void resolve(UUID id, Resolution request, UUID actor, String requestId);
    }
    public interface Notifications {
        Template template(UUID id, String requestId);
        NotificationAck enqueue(Enqueue request, String requestId);
    }
    public interface Payments {
        Subscription subscription(UUID user, String requestId);
        Page<Payment> payments(int page, int size, UUID user, String requestId);
    }
    public record UserPage(List<UserProfile> data, int page, int size, long total) {}
    public record UserState(UUID id, String displayName, boolean active) {}
    public record Moderation(UUID contentId, String status, String reason) {}
    public record Moderated(UUID contentId, String status, String reason, ContentItem data) {}
    public record Template(UUID id, String code, String titleTemplate, String bodyTemplate, boolean enabled) {}
    public record Enqueue(UUID userId, String templateCode, String title, String body) {}
    public record NotificationAck(UUID id, UUID userId) {}
}
