package com.erudit.content.controller;

import lombok.RequiredArgsConstructor;
import com.erudit.content.service.ContentReportService;

import com.erudit.openapi.api.ContentReportsApi;
import com.erudit.openapi.model.ContentReportRequest;
import com.erudit.openapi.model.ReportCreatedResponse;
import com.erudit.web.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ContentReportController implements ContentReportsApi {
    private final ContentReportService service;
    private final HttpServletRequest servletRequest;



    @Override
    public ResponseEntity<ReportCreatedResponse> reportContent(UUID id, ContentReportRequest request) {
        if (servletRequest.getUserPrincipal() == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        UUID reportId = service.report(id, request.getReason()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReportCreatedResponse(reportId));
    }
}
