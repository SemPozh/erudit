package com.erudit.user.web;

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
    private final HttpServletRequest request;

    @Override
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(response(profileService.get(currentUserId())));
    }

    @Override
    public ResponseEntity<UserProfileResponse> updateMyProfile(ProfileUpdateRequest profileUpdateRequest) {
        return ResponseEntity.ok(response(profileService.update(currentUserId(),
                profileUpdateRequest.getName(), profileUpdateRequest.getAvatarId())));
    }

    @Override public ResponseEntity<Void> deleteMyAccount() { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<Void> addPushToken(PushTokenRequest pushTokenRequest) { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<Void> deletePushToken(UUID id) { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<UserSettingsResponse> getMySettings() { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<UserSettingsResponse> updateMySettings(UserSettings userSettings) { return ResponseEntity.notFound().build(); }
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
}
