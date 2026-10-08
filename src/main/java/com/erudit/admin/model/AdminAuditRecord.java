package com.erudit.admin.model;
import java.time.Instant;import java.util.Map;import java.util.UUID;
public record AdminAuditRecord(UUID id,String actorId,String action,String targetType,String targetId,Instant occurredAt,Map<String,Object> metadata){}
