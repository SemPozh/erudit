package com.erudit.content;

import com.erudit.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content")
public class ContentReportController {
    private final ContentReportService service;

    public ContentReportController(ContentReportService service) {
        this.service = service;
    }

    @PostMapping("/{id}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UUID> report(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.success(service.report(id, body.get("reason")).id());
    }
}
