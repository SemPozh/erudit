package com.erudit.user.controller;

import com.erudit.user.model.User;
import com.erudit.user.model.UserSettings;
import com.erudit.user.openapi.api.UserSettingsApi;
import com.erudit.user.openapi.api.UsersApi;
import com.erudit.user.openapi.model.RegisterRequest;
import com.erudit.user.openapi.model.SettingsRequest;
import com.erudit.user.openapi.model.SettingsResponse;
import com.erudit.user.openapi.model.UpdateUserRequest;
import com.erudit.user.openapi.model.UserResponse;
import com.erudit.user.openapi.model.VerifyRequest;
import com.erudit.user.service.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor
public class UserController implements UsersApi, UserSettingsApi {
    private final UserService service;
    @Override public ResponseEntity<UserResponse> register(RegisterRequest request){return ResponseEntity.status(201).body(response(service.register(request.getEmail(),request.getDisplayName())));}
    @Override public ResponseEntity<Void> verifyEmail(VerifyRequest request){service.verify(request.getToken());return ResponseEntity.noContent().build();}
    @Override public ResponseEntity<List<UserResponse>> searchUsers(String query,Integer page,Integer size){var result=service.search(query,page,size);return ResponseEntity.ok().header("X-Total-Count",Long.toString(result.total())).body(result.items().stream().map(UserController::response).toList());}
    @Override public ResponseEntity<UserResponse> getUser(UUID id){return ResponseEntity.ok(response(service.get(id)));}
    @Override public ResponseEntity<UserResponse> updateUser(UUID id,UpdateUserRequest request){return ResponseEntity.ok(response(service.update(id,request.getDisplayName())));}
    @Override public ResponseEntity<Void> deleteUser(UUID id){service.delete(id);return ResponseEntity.noContent().build();}
    @Override public ResponseEntity<SettingsResponse> getSettings(UUID id){return ResponseEntity.ok(response(service.settings(id)));}
    @Override public ResponseEntity<SettingsResponse> updateSettings(UUID id,SettingsRequest request){return ResponseEntity.ok(response(service.settings(id,request.getLocale(),request.getDailyGoal())));}
    private static UserResponse response(User v){return new UserResponse(v.id(),v.email(),v.displayName(),v.verified(),v.createdAt());}
    private static SettingsResponse response(UserSettings v){return new SettingsResponse(v.locale(),v.dailyGoal(),v.userId());}
}
