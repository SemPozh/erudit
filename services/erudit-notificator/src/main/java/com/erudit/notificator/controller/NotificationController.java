package com.erudit.notificator.controller;

import com.erudit.notificator.model.NotificationRecord;
import com.erudit.notificator.model.Preference;
import com.erudit.notificator.openapi.api.NotificationsApi;
import com.erudit.notificator.openapi.model.NotificationPageResponse;
import com.erudit.notificator.openapi.model.NotificationResponse;
import com.erudit.notificator.openapi.model.PreferenceRequest;
import com.erudit.notificator.openapi.model.PreferenceResponse;
import com.erudit.notificator.openapi.model.UnreadResponse;
import com.erudit.notificator.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class NotificationController implements NotificationsApi {
    private final NotificationService service;
    @Override public Mono<ResponseEntity<PreferenceResponse>> getPreferences(UUID user,ServerWebExchange exchange){return service.preference(user).map(NotificationController::preference).map(ResponseEntity::ok);}
    @Override public Mono<ResponseEntity<PreferenceResponse>> updatePreferences(Mono<PreferenceRequest> request,UUID user,ServerWebExchange exchange){return request.flatMap(v->service.savePreference(new Preference(user,v.getEnabled(),v.getChannels().stream().map(Enum::name).toList()))).map(NotificationController::preference).map(ResponseEntity::ok);}
    @Override public Mono<ResponseEntity<NotificationPageResponse>> listNotifications(UUID user,Integer page,Integer size,ServerWebExchange exchange){return service.list(user,page,size).map(v->ResponseEntity.ok().header("X-Total-Count",Long.toString(v.total())).body(new NotificationPageResponse(v.items().stream().map(NotificationController::notification).toList(),v.page(),v.size(),v.total())));}
    @Override public Mono<ResponseEntity<Void>> markRead(UUID id,UUID user,ServerWebExchange exchange){return service.read(user,id).thenReturn(ResponseEntity.noContent().build());}
    @Override public Mono<ResponseEntity<Void>> markAllRead(UUID user,ServerWebExchange exchange){return service.readAll(user).thenReturn(ResponseEntity.noContent().build());}
    @Override public Mono<ResponseEntity<UnreadResponse>> unreadCount(UUID user,ServerWebExchange exchange){return service.unread(user).map(v->ResponseEntity.ok(new UnreadResponse(v)));}
    static NotificationResponse notification(NotificationRecord v){return new NotificationResponse(v.id(),v.userId(),v.templateCode(),v.title(),v.body(),v.read(),v.createdAt());}
    static PreferenceResponse preference(Preference v){return new PreferenceResponse(v.enabled(),v.channels().stream().map(PreferenceResponse.ChannelsEnum::fromValue).toList(),v.userId());}
}
