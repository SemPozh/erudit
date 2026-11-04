package com.erudit.user.service;

import com.erudit.user.model.AdminUser;
import com.erudit.user.model.SocialModels.InternalUser;
import com.erudit.user.repository.SocialRepository;
import com.erudit.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class InternalUserService {
    private final UserRepository users;
    private final SocialRepository social;
    @Transactional(readOnly=true) public InternalUser lookup(UUID id) {
        var user=social.internal(id);
        return new InternalUser(user.id(),user.displayName(),user.active()&&!users.blocked(id),user.deviceIds());
    }
    @Transactional(readOnly=true) public Page search(String query,int page,int size) {
        if(page<0||size<1||size>50)throw new IllegalArgumentException("Invalid page or size");
        return new Page(users.adminSearch(query,page,size),users.adminCount(query));
    }
    @Transactional public boolean block(UUID id){return users.block(id);}
    public record Page(List<AdminUser> data,long total){}
}
