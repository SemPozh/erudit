package com.erudit.friend.service;

import com.erudit.friend.dto.FriendPageResponse;
import com.erudit.friend.repository.FriendRepository;

import com.erudit.web.exception.ConflictException;
import com.erudit.web.exception.NotFoundException;
import com.erudit.web.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class FriendService {
    private final FriendRepository repository;
    private final Clock clock;
    public FriendService(FriendRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }

    @Transactional public UUID send(UUID user, UUID target) {
        if (user.equals(target)) throw new ValidationException("Cannot add yourself as a friend");
        if (!repository.userExists(target)) throw new NotFoundException("User not found");
        if (repository.relationExists(user, target)) throw new ConflictException("FriendDataResponse relation already exists");
        return repository.create(user, target, clock.instant());
    }
    @Transactional public void accept(UUID user, UUID id) { resolve(user,id,"ACCEPTED"); }
    @Transactional public void reject(UUID user, UUID id) { resolve(user,id,"REJECTED"); }
    private void resolve(UUID user, UUID id, String status) {
        if (!repository.resolve(id,user,status,clock.instant())) throw new NotFoundException("FriendDataResponse request not found");
    }
    public FriendPageResponse requests(UUID user,Integer page,Integer size) { int[] p=page(page,size); return new FriendPageResponse(repository.incoming(user,p[0],p[1]),repository.incomingCount(user),p[0],p[1]); }
    public FriendPageResponse friends(UUID user,Integer page,Integer size) { int[] p=page(page,size); return new FriendPageResponse(repository.friends(user,p[0],p[1]),repository.friendsCount(user),p[0],p[1]); }
    @Transactional public void remove(UUID user, UUID friend) { if(!repository.remove(user,friend)) throw new NotFoundException("FriendDataResponse not found"); }
    public boolean areFriends(UUID first, UUID second) { return repository.areFriends(first,second); }
    private int[] page(Integer p,Integer s) { int page=p==null?0:p,size=s==null?20:s; if(page<0||size<1||size>50) throw new ValidationException("Invalid page"); return new int[]{page,size}; }
}
