package com.erudit.notification;

import com.erudit.openapi.api.NotificationsApi;
import com.erudit.openapi.model.CountResponse;
import com.erudit.openapi.model.NotificationListResponse;
import com.erudit.openapi.model.NotificationPreferences;
import com.erudit.openapi.model.NotificationPreferencesResponse;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class NotificationController implements NotificationsApi {
    private final NotificationPreferenceService preferenceService;
    private final HttpServletRequest servletRequest;

    public NotificationController(NotificationPreferenceService preferenceService,
                                  HttpServletRequest servletRequest) {
        this.preferenceService = preferenceService;
        this.servletRequest = servletRequest;
    }

    @Override
    public ResponseEntity<NotificationPreferencesResponse> getNotificationPreferences() {
        return ResponseEntity.ok(response(preferenceService.get(currentUser())));
    }

    @Override
    public ResponseEntity<NotificationPreferencesResponse> updateNotificationPreferences(
            NotificationPreferences request) {
        return ResponseEntity.ok(response(preferenceService.update(currentUser(),
                request.getChannels().stream().map(value -> NotificationChannel.valueOf(value.name())).toList(),
                request.getTypes().stream().map(value -> NotificationType.valueOf(value.name())).toList(),
                request.getQuietHoursStart(), request.getQuietHoursEnd())));
    }

    @Override public ResponseEntity<NotificationListResponse> listNotifications(Integer page, Integer size) {
        return ResponseEntity.notFound().build();
    }
    @Override public ResponseEntity<Void> markNotificationRead(UUID id) { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<Void> markAllNotificationsRead() { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<CountResponse> getUnreadCount() { return ResponseEntity.notFound().build(); }

    private NotificationPreferencesResponse response(NotificationPreference value) {
        var data = new NotificationPreferences()
                .channels(value.channels().stream().map(channel ->
                        NotificationPreferences.ChannelsEnum.fromValue(channel.name()))
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))
                .types(value.types().stream().map(type ->
                        NotificationPreferences.TypesEnum.fromValue(type.name()))
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
        if (value.quietHoursStart() != null) {
            data.quietHoursStart(value.quietHoursStart().toString());
            data.quietHoursEnd(value.quietHoursEnd().toString());
        }
        return new NotificationPreferencesResponse(data);
    }

    private UUID currentUser() {
        if (servletRequest.getUserPrincipal() == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        try {
            return UUID.fromString(servletRequest.getUserPrincipal().getName());
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Authenticated user id is invalid");
        }
    }
}
