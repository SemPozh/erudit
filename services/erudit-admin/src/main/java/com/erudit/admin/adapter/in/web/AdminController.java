package com.erudit.admin.adapter.in.web;

import com.erudit.admin.openapi.api.AdminApi;
import com.erudit.admin.openapi.model.*;
import com.erudit.admin.application.AdminFacadeService;
import com.erudit.admin.application.AdminOperationRunner.Context;
import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {
    private final AdminFacadeService service;
    private final AdminDtoMapper mapper;
    private final HttpServletRequest request;

    private Context context(UUID actor) {
        return new Context(actor == null ? new UUID(0, 1) : actor, request.getHeader("X-Request-ID") == null ? UUID.randomUUID().toString() : request.getHeader("X-Request-ID"));
    }

    @Override
    public ResponseEntity<AdminUserListResponse> listAdminUsers(Integer page, Integer size, String query, UUID xUserId) {
        return mapper.page(service.users(context(xUserId), page, size, query), AdminUserListResponse.class);
    }

    @Override
    public ResponseEntity<Void> blockUser(UUID id, UUID xUserId) {
        service.block(context(xUserId), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ContentListResponse> listModerationQueue(Integer page, Integer size, UUID xUserId) {
        return mapper.page(service.queue(context(xUserId), page, size), ContentListResponse.class);
    }

    @Override
    public ResponseEntity<ContentItemResponse> approveContent(UUID id, UUID xUserId) {
        return ResponseEntity.ok(new ContentItemResponse(mapper.convert(service.moderate(context(xUserId), id, "PUBLISHED"), ContentItemDataResponse.class)));
    }

    @Override
    public ResponseEntity<ContentItemResponse> rejectContent(UUID id, UUID xUserId) {
        return ResponseEntity.ok(new ContentItemResponse(mapper.convert(service.moderate(context(xUserId), id, "REJECTED"), ContentItemDataResponse.class)));
    }

    @Override
    public ResponseEntity<ContentReportListResponse> listContentReports(Integer page, Integer size, UUID xUserId) {
        return mapper.page(service.reports(context(xUserId), page, size), ContentReportListResponse.class);
    }

    @Override
    public ResponseEntity<Void> resolveContentReport(UUID id, ReportResolutionRequest body, UUID xUserId) {
        service.resolve(context(xUserId), id, mapper.convert(body, com.erudit.admin.application.AdminModels.Resolution.class));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CountResponse> sendManyNotifications(NotificationSendManyRequest body, UUID xUserId) {
        return ResponseEntity.ok(new CountResponse(new CountDataResponse(service.sendMany(context(xUserId), mapper.convert(body, com.erudit.admin.application.AdminModels.SendMany.class)))));
    }

    @Override
    public ResponseEntity<AuditListResponse> listAuditLog(Integer page, Integer size, UUID actorId, String action, OffsetDateTime from, OffsetDateTime to, UUID xUserId) {
        return mapper.audit(service.audit(page, size, actorId, action, from, to));
    }

    @Override
    public ResponseEntity<SubscriptionResponse> viewUserSubscription(UUID id, UUID xUserId) {
        return ResponseEntity.ok(new SubscriptionResponse(mapper.convert(service.subscription(context(xUserId), id), SubscriptionDataResponse.class)));
    }

    @Override
    public ResponseEntity<PaymentListResponse> viewUserPayments(UUID id, Integer page, Integer size, UUID xUserId) {
        return mapper.page(service.payments(context(xUserId), id, page, size), PaymentListResponse.class);
    }
}
