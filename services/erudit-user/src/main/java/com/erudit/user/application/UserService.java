package com.erudit.user.application;

import com.erudit.user.domain.User;
import com.erudit.user.domain.UserSettings;
import com.erudit.user.application.port.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import com.erudit.user.application.port.Passwords;

@RequiredArgsConstructor
public class UserService {
    public static final String DEMO_VERIFICATION_TOKEN = "demo-verification-token";
    private final UserRepository repository;
    private final Passwords passwordEncoder;
    public User register(String email,String name,String password,String role){return repository.create(email.trim().toLowerCase(),name.trim(),passwordEncoder.encode(password),role,DEMO_VERIFICATION_TOKEN,OffsetDateTime.now().plusHours(24));}
    public void verify(String token){if(!repository.verify(token))throw new IllegalArgumentException("Verification token is invalid or expired");}
    public User get(UUID id){return repository.get(id).orElseThrow(()->new IllegalArgumentException("User not found"));}
    public Page search(String query,int page,int size){if(page<0||size<1||size>50)throw new IllegalArgumentException("Invalid page or size");return new Page(repository.search(query,page,size),repository.count(query));}
    public User update(UUID id,String name){return repository.update(id,name.trim());}
    public void delete(UUID id){repository.delete(id);}
    public UserSettings settings(UUID id){return repository.settings(id);}
    public UserSettings settings(UUID id,String locale,int goal){return repository.save(new UserSettings(id,locale,goal));}
    public record Page(List<User> items,long total) {}
}
