package com.erudit.user.controller;
import com.erudit.user.openapi.api.*;import com.erudit.user.openapi.model.*;import com.erudit.user.service.SocialService;import java.util.*;import lombok.RequiredArgsConstructor;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.RestController;
@RestController @RequiredArgsConstructor public class SocialController implements FriendsApi,DevicesApi {private final SocialService service;private static final UUID DEMO=UUID.fromString("00000000-0000-0000-0000-000000000001");private static UUID user(UUID id){return id==null?DEMO:id;}
 @Override public ResponseEntity<List<PublicUserResponse>> listFriends(UUID owner,Integer page,Integer size){return ResponseEntity.ok(service.friends(user(owner),page,size).stream().map(v->new PublicUserResponse(v.id(),v.displayName())).toList());}
 @Override public ResponseEntity<Void> requestFriend(FriendRequest request,UUID owner){service.request(user(owner),request.getUserId());return ResponseEntity.accepted().build();}
 @Override public ResponseEntity<Void> decideFriendRequest(UUID id,String decision,UUID owner){service.decide(user(owner),id,decision);return ResponseEntity.noContent().build();}
 @Override public ResponseEntity<Void> removeFriend(UUID friendId,UUID owner){service.remove(user(owner),friendId);return ResponseEntity.noContent().build();}
 @Override public ResponseEntity<DeviceResponse> registerDevice(DeviceRequest request,UUID owner){var v=service.device(user(owner),request.getPushToken(),request.getPlatform().name());return ResponseEntity.status(201).body(new DeviceResponse(v.id(),v.platform()));}
 @Override public ResponseEntity<Void> revokeDevice(UUID id,UUID owner){service.revoke(user(owner),id);return ResponseEntity.noContent().build();}
}
