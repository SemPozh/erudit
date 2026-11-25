package com.erudit.admin.domain;
import java.time.OffsetDateTime;
import java.util.UUID;
public record AdminAudit(UUID id,UUID actorId,String action,String targetType,String targetId,
        String status,String requestId,String metadata,OffsetDateTime occurredAt) {}
