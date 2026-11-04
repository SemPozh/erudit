package com.erudit.user.service;

import com.erudit.user.model.User;
import com.erudit.user.model.UserSettings;
import com.erudit.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class UserService {
    public static final String DEMO_VERIFICATION_TOKEN = "demo-verification-token";
    private final UserRepository repository;
    @Transactional public User register(String email,String name){try{return repository.create(email.trim().toLowerCase(),name.trim(),DEMO_VERIFICATION_TOKEN,OffsetDateTime.now().plusHours(24));}catch(DuplicateKeyException e){throw new IllegalStateException("Email already registered",e);}}
    @Transactional public void verify(String token){if(!repository.verify(token))throw new IllegalArgumentException("Verification token is invalid or expired");}
    public User get(UUID id){return repository.get(id).orElseThrow(()->new IllegalArgumentException("User not found"));}
    public Page search(String query,int page,int size){if(page<0||size<1||size>50)throw new IllegalArgumentException("Invalid page or size");return new Page(repository.search(query,page,size),repository.count(query));}
    @Transactional public User update(UUID id,String name){return repository.update(id,name.trim());}
    @Transactional public void delete(UUID id){repository.delete(id);}
    public UserSettings settings(UUID id){return repository.settings(id);}
    @Transactional public UserSettings settings(UUID id,String locale,int goal){return repository.save(new UserSettings(id,locale,goal));}
    public record Page(List<User> items,long total) {}
}
