package com.erudit.admin.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminModels {
    private AdminModels() {}
    public record Page<T>(List<T> data, Pagination pagination, String total) {}
    public record Pagination(int page, int size, long totalElements, int totalPages) {}
    public record UserProfile(UUID id, String email, String name, String avatarUrl, OffsetDateTime registeredAt, String status) {}
    public record ContentItem(UUID id, UUID categoryId, String type, String title, String description, String body,
            String mediaUrl, String difficulty, Integer estimatedMinutes, String authorId, String status,
            List<String> tags, Boolean premiumLocked) {}
    public record ContentReport(UUID id, UUID contentId, String reason, String status, OffsetDateTime createdAt) {}
    public record Resolution(String decision, String comment) {}
    public record SendMany(List<UUID> userIds, UUID templateId, Map<String, String> parameters) {}
    public record Subscription(UUID id, String plan, String status, OffsetDateTime expiresAt) {}
    public record Payment(UUID id, Double amount, String currency, String status, OffsetDateTime paidAt) {}
}
