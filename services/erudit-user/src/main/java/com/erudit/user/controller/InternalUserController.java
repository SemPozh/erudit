package com.erudit.user.controller;
import com.erudit.user.openapi.api.InternalUsersApi;
import com.erudit.user.openapi.model.UserLookupResponse;
import com.erudit.user.repository.SocialRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class InternalUserController implements InternalUsersApi {
    private final SocialRepository users;
    public InternalUserController(SocialRepository users) { this.users = users; }
    @Override
    public ResponseEntity<UserLookupResponse> lookupUser(UUID id) {
        try {
            var user = users.internal(id);
            return ResponseEntity.ok(new UserLookupResponse(user.id(), user.displayName(), user.active(), user.deviceIds()));
        } catch (IllegalArgumentException missing) {
            return ResponseEntity.notFound().build();
        }
    }
}
