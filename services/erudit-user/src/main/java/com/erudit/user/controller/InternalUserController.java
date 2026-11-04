package com.erudit.user.controller;
import com.erudit.user.openapi.api.InternalUsersApi;
import com.erudit.user.openapi.model.UserLookupResponse;
import com.erudit.user.service.InternalUserService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class InternalUserController implements InternalUsersApi {
    private final InternalUserService users;
    public InternalUserController(InternalUserService users) { this.users = users; }
    @Override
    public ResponseEntity<UserLookupResponse> lookupUser(UUID id) {
        try {
            var user = users.lookup(id);
            return ResponseEntity.ok(new UserLookupResponse(user.id(), user.displayName(), user.active(), user.deviceIds()));
        } catch (IllegalArgumentException missing) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override public ResponseEntity<com.erudit.user.openapi.model.AdminUserPage> adminSearchUsers(String query,Integer page,Integer size){
        var result=users.search(query,page,size);
        var data=result.data().stream().map(u -> new com.erudit.user.openapi.model.AdminUserResponse(u.id(),u.email(),u.name(),u.registeredAt(),u.status())).toList();
        long total=result.total();
        return ResponseEntity.ok().header("X-Total-Count",Long.toString(total)).body(new com.erudit.user.openapi.model.AdminUserPage(data,page,size,total));
    }
    @Override public ResponseEntity<Void> adminBlockUser(UUID id){return users.block(id)?ResponseEntity.noContent().build():ResponseEntity.notFound().build();}
}
