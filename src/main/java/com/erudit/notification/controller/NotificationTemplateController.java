package com.erudit.notification.controller;

import lombok.RequiredArgsConstructor;
import com.erudit.notification.dto.NotificationTemplatePageResponse;
import com.erudit.notification.model.NotificationTemplate;
import com.erudit.notification.service.NotificationTemplateService;

import com.erudit.openapi.api.NotificationAdminApi;
import com.erudit.openapi.model.NotificationTemplateListResponse;
import com.erudit.openapi.model.NotificationTemplateRequest;
import com.erudit.openapi.model.NotificationTemplateResponse;
import com.erudit.openapi.model.PageMetadataResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NotificationTemplateController implements NotificationAdminApi {
    private final NotificationTemplateService service;



    @Override
    public ResponseEntity<NotificationTemplateListResponse> listNotificationTemplates(Integer page, Integer size) {
        NotificationTemplatePageResponse result = service.list(page, size);
        var pagination = new PageMetadataResponse(result.page(), result.size(), result.total(), result.totalPages());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new NotificationTemplateListResponse(result.items().stream()
                        .map(NotificationTemplateController::model).toList()).pagination(pagination));
    }

    @Override
    public ResponseEntity<NotificationTemplateResponse> createNotificationTemplate(
            NotificationTemplateRequest request) {
        NotificationTemplate created = service.create(request.getType(), request.getChannel(),
                request.getTitle(), request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(new NotificationTemplateResponse(model(created)));
    }

    @Override
    public ResponseEntity<NotificationTemplateResponse> updateNotificationTemplate(
            UUID id, NotificationTemplateRequest request) {
        return ResponseEntity.ok(new NotificationTemplateResponse(model(service.update(id, request.getType(),
                request.getChannel(), request.getTitle(), request.getBody()))));
    }

    private static com.erudit.openapi.model.NotificationTemplateDataResponse model(NotificationTemplate value) {
        return new com.erudit.openapi.model.NotificationTemplateDataResponse(
                value.type().name(), value.channel().name(), value.body())
                .id(value.id()).title(value.title()).parameters(value.parameters().stream().sorted()
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
    }

}
