package com.erudit.content.controller;

import com.erudit.content.dto.ContentPage;
import com.erudit.content.dto.ContentReportPage;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.model.ReportDecision;
import com.erudit.content.service.ContentReportService;
import com.erudit.content.service.ContentService;

import com.erudit.openapi.api.ContentModerationApi;
import com.erudit.openapi.model.ContentItemResponse;
import com.erudit.openapi.model.ContentListResponse;
import com.erudit.openapi.model.ContentReportListResponse;
import com.erudit.openapi.model.PageMetadata;
import com.erudit.openapi.model.ReportResolutionRequest;
import com.erudit.web.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ContentModerationController implements ContentModerationApi {
    private final ContentService service;
    private final ContentReportService reportService;
    private final HttpServletRequest servletRequest;

    public ContentModerationController(ContentService service, ContentReportService reportService,
                                       HttpServletRequest servletRequest) {
        this.service = service;
        this.reportService = reportService;
        this.servletRequest = servletRequest;
    }

    @Override
    public ResponseEntity<ContentListResponse> listModerationQueue(Integer page, Integer size) {
        requireAdmin();
        ContentPage result = service.moderationQueue(page, size);
        var pagination = new PageMetadata(result.page(), result.size(), result.total(), result.totalPages());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new ContentListResponse(result.items().stream().map(ContentController::item).toList())
                        .pagination(pagination));
    }

    @Override
    public ResponseEntity<ContentItemResponse> approveContent(UUID id) {
        requireAdmin();
        return ResponseEntity.ok(new ContentItemResponse(
                ContentController.item(service.moderate(id, ContentStatus.PUBLISHED))));
    }

    @Override
    public ResponseEntity<ContentItemResponse> rejectContent(UUID id) {
        requireAdmin();
        return ResponseEntity.ok(new ContentItemResponse(
                ContentController.item(service.moderate(id, ContentStatus.REJECTED))));
    }

    @Override
    public ResponseEntity<ContentReportListResponse> listContentReports(Integer page, Integer size) {
        requireAdmin();
        ContentReportPage result = reportService.openReports(page, size);
        var data = result.items().stream().map(report -> new com.erudit.openapi.model.ContentReportItem(
                report.id(), report.contentId(), report.status().name())
                .reason(report.reason()).createdAt(report.createdAt().atOffset(java.time.ZoneOffset.UTC))).toList();
        var pagination = new PageMetadata(result.page(), result.size(), result.total(), result.totalPages());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new ContentReportListResponse(data).pagination(pagination));
    }

    @Override
    public ResponseEntity<Void> resolveContentReport(UUID id, ReportResolutionRequest request) {
        requireAdmin();
        reportService.resolve(id, ReportDecision.valueOf(request.getDecision().getValue()),
                request.getComment(), currentAdmin());
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin() {
        if (!servletRequest.isUserInRole("ADMIN")) {
            throw new ForbiddenException("ADMIN role is required");
        }
    }

    private String currentAdmin() {
        return servletRequest.getUserPrincipal().getName();
    }
}
