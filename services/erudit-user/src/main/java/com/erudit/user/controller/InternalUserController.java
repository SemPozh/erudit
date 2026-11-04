package com.erudit.user.controller;
import com.erudit.user.openapi.api.InternalUsersApi;
import com.erudit.user.openapi.model.UserLookupResponse;
import com.erudit.user.repository.SocialRepository;
import com.erudit.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class InternalUserController implements InternalUsersApi {
    private final UserRepository users;
    private final SocialRepository social;
    public InternalUserController(UserRepository users, SocialRepository social) { this.users = users; this.social = social; }
    @Override
    public ResponseEntity<UserLookupResponse> lookupUser(UUID id) {
        try {
            var user = social.internal(id);
            return ResponseEntity.ok(new UserLookupResponse(user.id(), user.displayName(), user.active() && !users.blocked(id), user.deviceIds()));
        } catch (IllegalArgumentException missing) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override public ResponseEntity<com.erudit.user.openapi.model.AdminUserPage> adminSearchUsers(String query,Integer page,Integer size){
        if(page<0||size<1||size>50)throw new IllegalArgumentException("Invalid page or size");
        var data=users.adminSearch(query,page,size).stream().map(u -> new com.erudit.user.openapi.model.AdminUserResponse(u.id(),u.email(),u.name(),u.registeredAt(),u.status())).toList();
        long total=users.adminCount(query);
        return ResponseEntity.ok().header("X-Total-Count",Long.toString(total)).body(new com.erudit.user.openapi.model.AdminUserPage(data,page,size,total));
    }
    @Override public ResponseEntity<Void> adminBlockUser(UUID id){return users.block(id)?ResponseEntity.noContent().build():ResponseEntity.notFound().build();}
}
