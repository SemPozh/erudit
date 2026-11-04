package com.erudit.user.service;
import com.erudit.user.model.SocialModels.*;import com.erudit.user.repository.SocialRepository;import java.util.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor public class SocialService {private final SocialRepository repository;
 public List<PublicUser> friends(UUID user,int page,int size){if(page<0||size<1||size>50)throw new IllegalArgumentException("Invalid page or size");return repository.friends(user,page,size);}
 @Transactional public UUID request(UUID from,UUID to){if(from.equals(to))throw new IllegalArgumentException("Cannot friend yourself");return repository.request(from,to);}
 @Transactional public void decide(UUID owner,UUID id,String decision){repository.decide(owner,id,"accept".equals(decision));}
 @Transactional public void remove(UUID user,UUID friend){repository.remove(user,friend);}
 @Transactional public Device device(UUID user,String token,String platform){return repository.saveDevice(user,token,platform);}
 @Transactional public void revoke(UUID user,UUID id){repository.revokeDevice(user,id);}
 public InternalUser internal(UUID id){return repository.internal(id);}
}
