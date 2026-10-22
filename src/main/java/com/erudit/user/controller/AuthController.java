package com.erudit.user.controller;

import com.erudit.openapi.api.AuthApi;
import com.erudit.openapi.model.RegisterRequest;
import com.erudit.openapi.model.UserProfileDataResponse;
import com.erudit.openapi.model.UserProfileResponse;
import com.erudit.openapi.model.VerifyEmailRequest;
import com.erudit.user.service.EmailVerificationService;
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

    @Override
    public ResponseEntity<UserProfileResponse> register(RegisterRequest request) {
        com.erudit.user.dto.UserProfileResponse saved = userService.register(
                new com.erudit.user.dto.RegisterRequest(request.getEmail(), request.getPassword(), request.getName()));
        UserProfileDataResponse profile = new UserProfileDataResponse(saved.id(), saved.email(), saved.name())
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
}
