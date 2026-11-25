package com.erudit.admin.adapter.in.web;

import com.erudit.admin.application.AdminModels.Page;
import com.erudit.admin.application.port.AdminAuditRepository;
import com.erudit.admin.openapi.model.*;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AdminDtoMapper {
    private final JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public <T> T convert(Object source, Class<T> type) { return json.convertValue(source, type); }
    public <T> ResponseEntity<T> page(Page<?> page, Class<T> type) {
        var response = ResponseEntity.ok();
        if (page.total() != null) response.header("X-Total-Count", page.total());
        return response.body(convert(page, type));
    }
    public ResponseEntity<AuditListResponse> audit(AdminAuditRepository.Page page) {
        var data = page.items().stream().map(record -> {
            Map<String, Object> details = new HashMap<>(json.readValue(record.metadata(), Map.class));
            details.put("status", record.status());
            details.put("requestId", record.requestId());
            return new AuditRecordDataResponse(record.id(), record.actorId(), record.action(), record.occurredAt())
                    .targetType(record.targetType()).targetId(record.targetId()).metadata(details);
        }).toList();
        var pagination = new PageMetadataResponse(page.page(), page.size(), page.total(), (int) ((page.total()+page.size()-1)/page.size()));
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(page.total())).body(new AuditListResponse(data, pagination));
    }
}
