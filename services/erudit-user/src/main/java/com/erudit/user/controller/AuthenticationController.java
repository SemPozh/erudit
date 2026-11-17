package com.erudit.user.controller;

import com.erudit.user.openapi.api.AuthenticationApi;
import com.erudit.user.openapi.model.LoginRequest;
import com.erudit.user.openapi.model.LoginResponse;
import com.erudit.user.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthenticationController implements AuthenticationApi {
    private final AuthenticationService service;

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest request) {
        var token = service.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(new LoginResponse(token.value(), "Bearer", token.expiresIn().toSeconds()));
    }
}
