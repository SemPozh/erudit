package com.erudit.notificator.controller;
import com.erudit.notificator.model.Template;
import com.erudit.notificator.openapi.api.TemplatesApi;
import com.erudit.notificator.openapi.model.TemplateRequest;
import com.erudit.notificator.openapi.model.TemplateResponse;
import com.erudit.notificator.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
@RestController @RequiredArgsConstructor
public class TemplateController implements TemplatesApi{
 private final NotificationService service;
 @Override public Mono<ResponseEntity<TemplateResponse>> createTemplate(Mono<TemplateRequest> request,ServerWebExchange exchange){return request.flatMap(v->service.saveTemplate(new Template(UUID.randomUUID(),v.getCode(),v.getTitleTemplate(),v.getBodyTemplate(),v.getEnabled()))).map(TemplateController::response).map(v->ResponseEntity.status(201).body(v));}
 @Override public Mono<ResponseEntity<Flux<TemplateResponse>>> listTemplates(ServerWebExchange exchange){return Mono.just(ResponseEntity.ok(service.templates().map(TemplateController::response)));}
 @Override public Mono<ResponseEntity<TemplateResponse>> getTemplate(UUID id,ServerWebExchange exchange){return service.template(id).map(TemplateController::response).map(ResponseEntity::ok);}
 @Override public Mono<ResponseEntity<TemplateResponse>> updateTemplate(UUID id,Mono<TemplateRequest> request,ServerWebExchange exchange){return request.flatMap(v->service.updateTemplate(new Template(id,v.getCode(),v.getTitleTemplate(),v.getBodyTemplate(),v.getEnabled()))).map(TemplateController::response).map(ResponseEntity::ok);}
 @Override public Mono<ResponseEntity<Void>> deleteTemplate(UUID id,ServerWebExchange exchange){return service.deleteTemplate(id).thenReturn(ResponseEntity.noContent().build());}
 static TemplateResponse response(Template v){return new TemplateResponse(v.code(),v.title(),v.body(),v.enabled(),v.id());}
}
