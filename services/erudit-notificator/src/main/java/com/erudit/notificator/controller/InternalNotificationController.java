package com.erudit.notificator.controller;
import com.erudit.notificator.openapi.api.InternalNotificationsApi;
import com.erudit.notificator.openapi.model.EnqueueRequest;
import com.erudit.notificator.openapi.model.NotificationResponse;
import com.erudit.notificator.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
@RestController @RequiredArgsConstructor
public class InternalNotificationController implements InternalNotificationsApi{
 private final NotificationService service;
 @Override public Mono<ResponseEntity<NotificationResponse>> enqueue(Mono<EnqueueRequest> request,ServerWebExchange exchange){return request.flatMap(v->service.enqueue(v.getUserId(),v.getTemplateCode(),v.getTitle(),v.getBody())).map(NotificationController::notification).map(v->ResponseEntity.accepted().body(v));}
 @Override public Mono<ResponseEntity<Flux<NotificationResponse>>> enqueueBatch(Flux<EnqueueRequest> request,ServerWebExchange exchange){return request.map(v->new NotificationService.Enqueue(v.getUserId(),v.getTemplateCode(),v.getTitle(),v.getBody())).collectList().map(service::enqueue).map(v->ResponseEntity.accepted().body(v.map(NotificationController::notification)));}
}
