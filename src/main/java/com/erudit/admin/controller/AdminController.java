package com.erudit.admin.controller;

import com.erudit.admin.model.AdminAuditRecord;
import com.erudit.admin.service.AdminService;
import com.erudit.openapi.api.AdminApi;import com.erudit.openapi.model.*;import com.erudit.user.model.User;import com.erudit.web.exception.UnauthorizedException;import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.*;import org.springframework.web.bind.annotation.RestController;import java.time.*;import java.util.*;
@RestController public class AdminController implements AdminApi {
 private final AdminService service;private final HttpServletRequest request;public AdminController(AdminService service,HttpServletRequest request){this.service=service;this.request=request;}
 public ResponseEntity<AdminUserListResponse> listAdminUsers(Integer page,Integer size,String query){var p=service.users(query,page,size);var r=new AdminUserListResponse(p.getContent().stream().map(this::profile).toList());r.setPagination(meta(p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages()));return ResponseEntity.ok(r);}
 public ResponseEntity<Void> blockUser(UUID id){service.block(actor(),id);return ResponseEntity.noContent().build();}
 public ResponseEntity<CountResponse> sendManyNotifications(NotificationSendManyRequest body){long n=service.sendMany(actor(),body.getUserIds(),body.getTemplateId(),body.getParameters());return ResponseEntity.ok(new CountResponse(new Count(n)));}
 public ResponseEntity<AuditListResponse> listAuditLog(Integer page,Integer size,UUID actorId,String action,OffsetDateTime from,OffsetDateTime to){var p=service.audit(page,size,actorId,action,from==null?null:from.toInstant(),to==null?null:to.toInstant());var data=p.items().stream().map(this::audit).toList();return ResponseEntity.ok(new AuditListResponse(data,meta(p.page(),p.size(),p.total(),(int)Math.ceil(p.total()/(double)p.size()))));}
 private UserProfile profile(User u){return new UserProfile(u.getId(),u.getEmail(),u.getName()).avatarUrl(u.getAvatar()).registeredAt(u.getCreatedAt().atOffset(ZoneOffset.UTC)).status(u.getStatus().name());}
 private AuditRecord audit(AdminAuditRecord a){UUID actor;try{actor=UUID.fromString(a.actorId());}catch(Exception e){actor=new UUID(0,0);}return new AuditRecord(a.id(),actor,a.action(),a.occurredAt().atOffset(ZoneOffset.UTC)).targetType(a.targetType()).targetId(a.targetId()).metadata(a.metadata());}
 private PageMetadata meta(int page,int size,long total,int pages){return new PageMetadata(page,size,total,pages);}
 private UUID actor(){if(request.getUserPrincipal()==null)throw new UnauthorizedException("Authentication is required");try{return UUID.fromString(request.getUserPrincipal().getName());}catch(Exception e){throw new UnauthorizedException("Invalid administrator identity");}}
}
