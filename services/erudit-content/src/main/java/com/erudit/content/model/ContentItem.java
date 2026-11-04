package com.erudit.content.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContentItem(UUID id, UUID categoryId, String type, String title, String description,
                          String body, String mediaUrl, String difficulty, int estimatedMinutes,
                          boolean premiumLocked, String status, List<String> tags, Instant createdAt) {
}
