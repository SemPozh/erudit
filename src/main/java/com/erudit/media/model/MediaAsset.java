package com.erudit.media.model;

import java.time.Instant;
import java.util.UUID;

public record MediaAsset(UUID id, String storageKey, String originalName,
                         String contentType, long size, Instant createdAt) {
}
