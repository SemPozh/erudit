package com.erudit.content;

import com.erudit.openapi.api.ContentReportsApi;
import com.erudit.openapi.model.ReportCreatedResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ContentReportController implements ContentReportsApi {
    private final ContentReportService service;

    public ContentReportController(ContentReportService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ReportCreatedResponse> reportContent(UUID id, ContentReportRequest request) {
        UUID reportId = service.report(id, request.reason()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReportCreatedResponse(reportId));
    }
}
