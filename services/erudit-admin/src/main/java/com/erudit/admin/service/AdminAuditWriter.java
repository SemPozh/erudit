package com.erudit.admin.service;
import com.erudit.admin.model.AdminAudit;
import com.erudit.admin.repository.AdminAuditRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
@Service
public class AdminAuditWriter {
    private final AdminAuditRepository repository;
    private final JsonMapper json=JsonMapper.builder().build();
    public AdminAuditWriter(AdminAuditRepository repository){this.repository=repository;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public UUID start(UUID actor,String requestId,String action,String targetType,String targetId){
        UUID id=UUID.randomUUID();repository.insert(new AdminAudit(id,actor,action,targetType,targetId,"RUNNING",requestId,"{}",OffsetDateTime.now(java.time.ZoneOffset.UTC)));return id;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(UUID id,String status,Map<String,Object> details){repository.finish(id,status,json.writeValueAsString(details));}
}
