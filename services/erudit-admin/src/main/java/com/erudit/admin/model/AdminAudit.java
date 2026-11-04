package com.erudit.admin.model;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
@Table("admin_audit")
public record AdminAudit(@Id UUID id,UUID actorId,String action,String targetType,String targetId,
        String status,String requestId,String metadata,OffsetDateTime occurredAt) {}
