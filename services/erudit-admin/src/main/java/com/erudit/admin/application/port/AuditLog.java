package com.erudit.admin.application.port;

import java.util.Map;
import java.util.UUID;

public interface AuditLog {
    UUID start(UUID actor, String requestId, String action, String targetType, String targetId);
    void finish(UUID id, String status, Map<String, Object> details);
}
