package com.erudit.admin.application.port;

import com.erudit.admin.domain.AdminAudit;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AdminAuditRepository {
    Page list(int page, int size, UUID actor, String action, OffsetDateTime from, OffsetDateTime to);
    record Page(List<AdminAudit> items, long total, int page, int size) {}
}
