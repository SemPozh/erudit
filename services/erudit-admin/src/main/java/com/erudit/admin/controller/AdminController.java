package com.erudit.admin.controller;
import com.erudit.admin.openapi.api.AdminApi;
import com.erudit.admin.openapi.model.*;
import com.erudit.admin.service.AdminFacadeService;
import com.erudit.admin.service.AdminOperationRunner.Context;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequiredArgsConstructor
public class AdminController implements AdminApi {
    private final AdminFacadeService service;
    private final HttpServletRequest request;
    private Context context(UUID actor){return new Context(actor==null?new UUID(0,1):actor,request.getHeader("X-Request-ID")==null?UUID.randomUUID().toString():request.getHeader("X-Request-ID"));}
    @Override public ResponseEntity<AdminUserListResponse> listAdminUsers(Integer page,Integer size,String query,UUID xUserId){return service.users(context(xUserId),page,size,query);}
    @Override public ResponseEntity<Void> blockUser(UUID id,UUID xUserId){service.block(context(xUserId),id);return ResponseEntity.noContent().build();}
    @Override public ResponseEntity<ContentListResponse> listModerationQueue(Integer page,Integer size,UUID xUserId){return service.queue(context(xUserId),page,size);}
    @Override public ResponseEntity<ContentItemResponse> approveContent(UUID id,UUID xUserId){return ResponseEntity.ok(service.moderate(context(xUserId),id,"PUBLISHED"));}
    @Override public ResponseEntity<ContentItemResponse> rejectContent(UUID id,UUID xUserId){return ResponseEntity.ok(service.moderate(context(xUserId),id,"REJECTED"));}
    @Override public ResponseEntity<ContentReportListResponse> listContentReports(Integer page,Integer size,UUID xUserId){return service.reports(context(xUserId),page,size);}
    @Override public ResponseEntity<Void> resolveContentReport(UUID id,ReportResolutionRequest body,UUID xUserId){service.resolve(context(xUserId),id,body);return ResponseEntity.noContent().build();}
    @Override public ResponseEntity<CountResponse> sendManyNotifications(NotificationSendManyRequest body,UUID xUserId){return ResponseEntity.ok(new CountResponse(new CountDataResponse(service.sendMany(context(xUserId),body))));}
    @Override public ResponseEntity<AuditListResponse> listAuditLog(Integer page,Integer size,UUID actorId,String action,OffsetDateTime from,OffsetDateTime to,UUID xUserId){return service.audit(page,size,actorId,action,from,to);}
    @Override public ResponseEntity<SubscriptionResponse> viewUserSubscription(UUID id,UUID xUserId){return ResponseEntity.ok(service.subscription(context(xUserId),id));}
    @Override public ResponseEntity<PaymentListResponse> viewUserPayments(UUID id,Integer page,Integer size,UUID xUserId){return service.payments(context(xUserId),id,page,size);}
}
