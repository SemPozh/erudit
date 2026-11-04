package com.erudit.user.controller;
import com.erudit.user.openapi.api.InternalUsersApi;
import com.erudit.user.openapi.model.UserLookupResponse;
import com.erudit.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class InternalUserController implements InternalUsersApi {
    private final UserRepository users;
    public InternalUserController(UserRepository users) { this.users = users; }
    @Override
    public ResponseEntity<UserLookupResponse> lookupUser(UUID id) {
        return users.get(id).map(user -> ResponseEntity.ok(new UserLookupResponse(user.id(), user.displayName(), true)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
