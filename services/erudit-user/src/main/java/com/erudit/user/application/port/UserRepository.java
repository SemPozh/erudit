package com.erudit.user.application.port;

import com.erudit.user.domain.*;
import com.erudit.user.domain.SocialModels.*;
import java.time.OffsetDateTime;
import java.util.*;

public interface UserRepository {
    User create(String email,String name,String passwordHash,String role,String token,OffsetDateTime expires);
    Optional<User> get(UUID id);
    Optional<UserCredentials> credentials(String email);
    List<User> search(String query,int page,int size);
    long count(String query);
    boolean verify(String token);
    User update(UUID id,String name);
    void delete(UUID id);
    UserSettings settings(UUID id);
    UserSettings save(UserSettings v);
    boolean blocked(UUID id);
    boolean block(UUID id);
    List<com.erudit.user.domain.AdminUser> adminSearch(String query,int page,int size);
    long adminCount(String query);
}
