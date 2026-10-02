package com.erudit.media;

import java.time.Instant;
import java.util.UUID;

public record MediaAsset(UUID id, String storageKey, String originalName,
                         String contentType, long size, Instant createdAt) {
}
