package com.erudit.content.controller;

import lombok.RequiredArgsConstructor;
import com.erudit.content.service.ContentReportService;

import com.erudit.openapi.api.ContentReportsApi;
import com.erudit.openapi.model.ContentReportRequest;
import com.erudit.openapi.model.ReportCreatedResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ContentReportController implements ContentReportsApi {
    private final ContentReportService service;
    @Override
    public ResponseEntity<ReportCreatedResponse> reportContent(UUID id, ContentReportRequest request) {
        UUID reportId = service.report(id, request.getReason()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReportCreatedResponse(reportId));
    }
}
