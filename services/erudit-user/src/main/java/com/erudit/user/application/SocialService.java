package com.erudit.user.application;
import com.erudit.user.domain.SocialModels.*;import com.erudit.user.application.port.SocialRepository;import java.util.*;import lombok.RequiredArgsConstructor;@RequiredArgsConstructor public class SocialService {private final SocialRepository repository;
 public List<PublicUser> friends(UUID user,int page,int size){if(page<0||size<1||size>50)throw new IllegalArgumentException("Invalid page or size");return repository.friends(user,page,size);}
 public UUID request(UUID from,UUID to){if(from.equals(to))throw new IllegalArgumentException("Cannot friend yourself");return repository.request(from,to);}
 public void decide(UUID owner,UUID id,String decision){repository.decide(owner,id,"accept".equals(decision));}
 public void remove(UUID user,UUID friend){repository.remove(user,friend);}
 public Device device(UUID user,String token,String platform){return repository.saveDevice(user,token,platform);}
 public void revoke(UUID user,UUID id){repository.revokeDevice(user,id);}
 public InternalUser internal(UUID id){return repository.internal(id);}
}
