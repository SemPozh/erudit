package com.erudit.user.web;

import com.erudit.openapi.api.AuthApi;
import com.erudit.openapi.model.LoginRequest;
import com.erudit.openapi.model.RefreshRequest;
import com.erudit.openapi.model.RegisterRequest;
import com.erudit.openapi.model.ResetPasswordRequest;
import com.erudit.openapi.model.ResetRequest;
import com.erudit.openapi.model.TokenPair;
import com.erudit.openapi.model.TokenPairResponse;
import com.erudit.openapi.model.UserProfile;
import com.erudit.openapi.model.UserProfileResponse;
import com.erudit.openapi.model.VerifyEmailRequest;
import com.erudit.user.service.AuthenticationService;
import com.erudit.user.service.EmailVerificationService;
import com.erudit.user.service.IssuedTokenPair;
import com.erudit.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final AuthenticationService authenticationService;

    @Override
    public ResponseEntity<TokenPairResponse> login(LoginRequest request) {
        return ResponseEntity.ok(toResponse(authenticationService.login(request.getEmail(), request.getPassword())));
    }

    @Override
    public ResponseEntity<TokenPairResponse> refreshToken(RefreshRequest request) {
        return ResponseEntity.ok(toResponse(authenticationService.refresh(request.getRefreshToken())));
    }

    @Override
    public ResponseEntity<Void> logout(RefreshRequest request) {
        authenticationService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserProfileResponse> register(RegisterRequest request) {
        com.erudit.user.dto.UserProfileResponse saved = userService.register(
                new com.erudit.user.dto.RegisterRequest(request.getEmail(), request.getPassword(), request.getName()));
        UserProfile profile = new UserProfile(saved.id(), saved.email(), saved.name())
                .avatarUrl(saved.avatar())
                .registeredAt(saved.createdAt().atOffset(ZoneOffset.UTC))
                .status(saved.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserProfileResponse(profile));
    }

    @Override
    public ResponseEntity<Void> verifyEmail(VerifyEmailRequest request) {
        emailVerificationService.verify(request.getToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> logoutAll() {
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<Void> requestPasswordReset(ResetRequest request) {
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<Void> resetPassword(ResetPasswordRequest request) {
        return ResponseEntity.notFound().build();
    }

    private static TokenPairResponse toResponse(IssuedTokenPair issued) {
        TokenPair data = new TokenPair(issued.accessToken(), issued.refreshToken(), "Bearer",
                Math.toIntExact(issued.expiresInSeconds()));
        return new TokenPairResponse(data);
    }
}
