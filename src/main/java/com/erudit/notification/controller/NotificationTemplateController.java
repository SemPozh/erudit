package com.erudit.notification.controller;

import com.erudit.notification.dto.NotificationTemplatePage;
import com.erudit.notification.model.NotificationTemplate;
import com.erudit.notification.service.NotificationTemplateService;

import com.erudit.openapi.api.NotificationAdminApi;
import com.erudit.openapi.model.NotificationTemplateListResponse;
import com.erudit.openapi.model.NotificationTemplateResponse;
import com.erudit.openapi.model.PageMetadata;
import com.erudit.web.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class NotificationTemplateController implements NotificationAdminApi {
    private final NotificationTemplateService service;
    private final HttpServletRequest servletRequest;

    public NotificationTemplateController(NotificationTemplateService service,
                                          HttpServletRequest servletRequest) {
        this.service = service;
        this.servletRequest = servletRequest;
    }

    @Override
    public ResponseEntity<NotificationTemplateListResponse> listNotificationTemplates(Integer page, Integer size) {
        requireAdmin();
        NotificationTemplatePage result = service.list(page, size);
        var pagination = new PageMetadata(result.page(), result.size(), result.total(), result.totalPages());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new NotificationTemplateListResponse(result.items().stream()
                        .map(NotificationTemplateController::model).toList()).pagination(pagination));
    }

    @Override
    public ResponseEntity<NotificationTemplateResponse> createNotificationTemplate(
            com.erudit.openapi.model.NotificationTemplate request) {
        requireAdmin();
        NotificationTemplate created = service.create(request.getType(), request.getChannel(),
                request.getTitle(), request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(new NotificationTemplateResponse(model(created)));
    }

    @Override
    public ResponseEntity<NotificationTemplateResponse> updateNotificationTemplate(
            UUID id, com.erudit.openapi.model.NotificationTemplate request) {
        requireAdmin();
        return ResponseEntity.ok(new NotificationTemplateResponse(model(service.update(id, request.getType(),
                request.getChannel(), request.getTitle(), request.getBody()))));
    }

    private static com.erudit.openapi.model.NotificationTemplate model(NotificationTemplate value) {
        return new com.erudit.openapi.model.NotificationTemplate(
                value.type().name(), value.channel().name(), value.body())
                .id(value.id()).title(value.title()).parameters(value.parameters().stream().sorted()
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
    }

    private void requireAdmin() {
        if (!servletRequest.isUserInRole("ADMIN")) {
            throw new ForbiddenException("ADMIN role is required");
        }
    }
}
