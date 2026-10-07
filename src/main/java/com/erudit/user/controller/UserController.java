package com.erudit.user.controller;

import com.erudit.openapi.api.UsersApi;
import com.erudit.openapi.model.AdminUserListResponse;
import com.erudit.openapi.model.ProfileUpdateRequest;
import com.erudit.openapi.model.PushTokenRequest;
import com.erudit.openapi.model.UserProfile;
import com.erudit.openapi.model.UserProfileResponse;
import com.erudit.openapi.model.UserSettings;
import com.erudit.openapi.model.UserSettingsResponse;
import com.erudit.user.domain.User;
import com.erudit.user.service.UserProfileService;
import com.erudit.user.service.UserPreferenceSnapshot;
import com.erudit.user.service.UserPreferencesService;
import com.erudit.user.service.UserService;
import com.erudit.user.service.DevicePushTokenService;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {
    private final UserProfileService profileService;
    private final UserPreferencesService preferencesService;
    private final HttpServletRequest request;
    private final UserService userService;
    private final DevicePushTokenService pushTokenService;

    @Override
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(response(profileService.get(currentUserId())));
    }

    @Override
    public ResponseEntity<UserProfileResponse> updateMyProfile(ProfileUpdateRequest profileUpdateRequest) {
        return ResponseEntity.ok(response(profileService.update(currentUserId(),
                profileUpdateRequest.getName(), profileUpdateRequest.getAvatarId())));
    }

    @Override
    public ResponseEntity<Void> deleteMyAccount() {
        userService.deleteAccount(currentUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> addPushToken(PushTokenRequest pushTokenRequest) {
        pushTokenService.register(currentUserId(), pushTokenRequest.getToken(),
                pushTokenRequest.getPlatform().getValue());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deletePushToken(UUID id) {
        pushTokenService.revoke(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
    @Override
    public ResponseEntity<UserSettingsResponse> getMySettings() {
        return ResponseEntity.ok(settingsResponse(preferencesService.preferencesFor(currentUserId())));
    }

    @Override
    public ResponseEntity<UserSettingsResponse> updateMySettings(UserSettings settings) {
        return ResponseEntity.ok(settingsResponse(preferencesService.update(currentUserId(),
                settings.getLanguage(), settings.getTimeZone(), settings.getFavoriteCategories(),
                settings.getDailyGoalMinutes(), settings.getVisibleInSearch(),
                settings.getVisibleInRating(), settings.getAnalyticsConsent())));
    }
    @Override public ResponseEntity<AdminUserListResponse> searchUsers(String query, Integer page, Integer size) { return ResponseEntity.notFound().build(); }

    private UUID currentUserId() {
        if (request.getUserPrincipal() == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        try {
            return UUID.fromString(request.getUserPrincipal().getName());
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Access token subject is invalid");
        }
    }

    private static UserProfileResponse response(User user) {
        UserProfile data = new UserProfile(user.getId(), user.getEmail(), user.getName())
                .avatarUrl(user.getAvatar())
                .registeredAt(user.getCreatedAt().atOffset(ZoneOffset.UTC))
                .status(user.getStatus().name());
        return new UserProfileResponse(data);
    }

    private static UserSettingsResponse settingsResponse(UserPreferenceSnapshot value) {
        UserSettings data = new UserSettings()
                .language(value.language())
                .timeZone(value.timeZone())
                .favoriteCategories(
                        value.favoriteCategories().stream()
                                .sorted()
                                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
                )
                .dailyGoalMinutes(value.dailyGoalMinutes())
                .visibleInSearch(value.visibleInSearch())
                .visibleInRating(value.visibleInRating())
                .analyticsConsent(value.analyticsConsent());

        return new UserSettingsResponse(data);
    }
}
