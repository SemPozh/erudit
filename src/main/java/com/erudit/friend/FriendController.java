package com.erudit.friend;

import com.erudit.openapi.api.FriendsApi;
import com.erudit.openapi.model.*;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
public class FriendController implements FriendsApi {
    private final FriendService service; private final HttpServletRequest request;
    public FriendController(FriendService service,HttpServletRequest request){this.service=service;this.request=request;}
    public ResponseEntity<FriendRequestListResponse> sendFriendRequest(FriendRequestCreate body){UUID id=service.send(user(),body.getUserId());var item=new Friend();item.setId(id);item.setName("");item.setStatus("PENDING");return ResponseEntity.status(201).body(new FriendRequestListResponse(java.util.List.of(item)));}
    public ResponseEntity<FriendRequestListResponse> listFriendRequests(Integer page,Integer size){var p=service.requests(user(),page,size);return ResponseEntity.ok(requests(p));}
    public ResponseEntity<FriendListResponse> listFriends(Integer page,Integer size){var p=service.friends(user(),page,size);var response=new FriendListResponse(p.items().stream().map(this::model).toList());response.setPagination(meta(p));return ResponseEntity.ok(response);}
    public ResponseEntity<Void> acceptFriendRequest(UUID id){service.accept(user(),id);return ResponseEntity.noContent().build();}
    public ResponseEntity<Void> rejectFriendRequest(UUID id){service.reject(user(),id);return ResponseEntity.noContent().build();}
    public ResponseEntity<Void> removeFriend(UUID id){service.remove(user(),id);return ResponseEntity.noContent().build();}
    private FriendRequestListResponse requests(FriendPage p){var r=new FriendRequestListResponse(p.items().stream().map(this::model).toList());r.setPagination(meta(p));return r;}
    private Friend model(FriendRecord r){var m=new Friend();m.setId(r.id());m.setName(r.name());m.setStatus(r.status());return m;}
    private PageMetadata meta(FriendPage p){var m=new PageMetadata();m.setPage(p.page());m.setSize(p.size());m.setTotalElements(p.total());m.setTotalPages((int)Math.ceil(p.total()/(double)p.size()));return m;}
    private UUID user(){if(request.getUserPrincipal()==null)throw new UnauthorizedException("Authentication is required");try{return UUID.fromString(request.getUserPrincipal().getName());}catch(IllegalArgumentException e){throw new UnauthorizedException("Invalid authenticated user");}}
}
