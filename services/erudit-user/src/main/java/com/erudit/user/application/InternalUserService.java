package com.erudit.user.application;

import com.erudit.user.domain.AdminUser;
import com.erudit.user.domain.SocialModels.InternalUser;
import com.erudit.user.application.port.SocialRepository;
import com.erudit.user.application.port.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InternalUserService {
    private final UserRepository users;
    private final SocialRepository social;
    public InternalUser lookup(UUID id) {
        var user=social.internal(id);
        return new InternalUser(user.id(),user.displayName(),user.active()&&!users.blocked(id),user.deviceIds());
    }
    public Page search(String query,int page,int size) {
        if(page<0||size<1||size>50)throw new IllegalArgumentException("Invalid page or size");
        return new Page(users.adminSearch(query,page,size),users.adminCount(query));
    }
    public boolean block(UUID id){return users.block(id);}
    public record Page(List<AdminUser> data,long total){}
}
