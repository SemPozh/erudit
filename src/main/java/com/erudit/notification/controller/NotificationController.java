package com.erudit.notification.controller;

import com.erudit.notification.dto.UserNotificationPage;
import com.erudit.notification.model.NotificationChannel;
import com.erudit.notification.model.NotificationPreference;
import com.erudit.notification.model.NotificationType;
import com.erudit.notification.model.UserNotification;
import com.erudit.notification.service.NotificationPreferenceService;
import com.erudit.notification.service.UserNotificationService;

import com.erudit.openapi.api.NotificationsApi;
import com.erudit.openapi.model.*;
import com.erudit.web.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
public class NotificationController implements NotificationsApi {
 private final NotificationPreferenceService preferenceService;
 private final UserNotificationService notificationService;
 private final HttpServletRequest servletRequest;
 public NotificationController(NotificationPreferenceService preferenceService, UserNotificationService notificationService, HttpServletRequest servletRequest){this.preferenceService=preferenceService;this.notificationService=notificationService;this.servletRequest=servletRequest;}
 @Override public ResponseEntity<NotificationPreferencesResponse> getNotificationPreferences(){return ResponseEntity.ok(preferenceResponse(preferenceService.get(currentUser())));}
 @Override public ResponseEntity<NotificationPreferencesResponse> updateNotificationPreferences(NotificationPreferences request){return ResponseEntity.ok(preferenceResponse(preferenceService.update(currentUser(),request.getChannels().stream().map(v->NotificationChannel.valueOf(v.name())).toList(),request.getTypes().stream().map(v->NotificationType.valueOf(v.name())).toList(),request.getQuietHoursStart(),request.getQuietHoursEnd())));}
 @Override public ResponseEntity<NotificationListResponse> listNotifications(Integer page,Integer size){UserNotificationPage r=notificationService.list(currentUser(),page,size);var pagination=new PageMetadata(r.page(),r.size(),r.total(),r.totalPages());return ResponseEntity.ok().header("X-Total-Count",Long.toString(r.total())).body(new NotificationListResponse(r.items().stream().map(NotificationController::model).toList()).pagination(pagination));}
 @Override public ResponseEntity<Void> markNotificationRead(UUID id){notificationService.read(currentUser(),id);return ResponseEntity.noContent().build();}
 @Override public ResponseEntity<Void> markAllNotificationsRead(){notificationService.readAll(currentUser());return ResponseEntity.noContent().build();}
 @Override public ResponseEntity<CountResponse> getUnreadCount(){return ResponseEntity.ok(new CountResponse(new Count(notificationService.unread(currentUser()))));}
 private static com.erudit.openapi.model.Notification model(UserNotification v){return new com.erudit.openapi.model.Notification(v.id(),v.title(),v.read()).body(v.body()).createdAt(v.createdAt().atOffset(ZoneOffset.UTC));}
 private static NotificationPreferencesResponse preferenceResponse(NotificationPreference v){var data=new NotificationPreferences().channels(v.channels().stream().map(c->NotificationPreferences.ChannelsEnum.fromValue(c.name())).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))).types(v.types().stream().map(t->NotificationPreferences.TypesEnum.fromValue(t.name())).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));if(v.quietHoursStart()!=null){data.quietHoursStart(v.quietHoursStart().toString());data.quietHoursEnd(v.quietHoursEnd().toString());}return new NotificationPreferencesResponse(data);}
 private UUID currentUser(){if(servletRequest.getUserPrincipal()==null)throw new UnauthorizedException("Authentication is required");try{return UUID.fromString(servletRequest.getUserPrincipal().getName());}catch(IllegalArgumentException e){throw new UnauthorizedException("Authenticated user id is invalid");}}
}
