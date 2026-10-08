package com.erudit.admin.service;

import com.erudit.admin.dto.AdminAuditPage;
import com.erudit.admin.repository.AdminAuditRepository;
import com.erudit.notification.model.NotificationTemplate;
import com.erudit.notification.service.NotificationTemplateService;
import com.erudit.notification.service.UserNotificationService;
import com.erudit.user.model.*;import com.erudit.user.repository.UserRepository;import com.erudit.web.exception.*;import org.springframework.data.domain.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.*;import java.util.*;
@Service public class AdminService {
 private final UserRepository users;private final UserNotificationService notifications;private final NotificationTemplateService templates;private final AdminAuditRepository audit;private final Clock clock;
 public AdminService(UserRepository users,UserNotificationService notifications,NotificationTemplateService templates,AdminAuditRepository audit,Clock clock){this.users=users;this.notifications=notifications;this.templates=templates;this.audit=audit;this.clock=clock;}
 public Page<User> users(String query,Integer page,Integer size){int p=page==null?0:page,s=size==null?20:size;if(p<0||s<1||s>50)throw new ValidationException("Invalid page");return query==null||query.isBlank()?users.findAll(PageRequest.of(p,s,Sort.by("name").ascending().and(Sort.by("id")))):users.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query.trim(),query.trim(),PageRequest.of(p,s));}
 @Transactional public void block(UUID actor,UUID id){User user=users.findById(id).orElseThrow(()->new NotFoundException("User not found"));if(user.getRole()==UserRole.ADMIN)throw new ConflictException("Administrator cannot be blocked");user.setStatus(UserStatus.BLOCKED);users.save(user);audit.record(actor.toString(),"USER_BLOCKED","USER",id.toString(),Map.of("result","SUCCESS"),clock.instant());}
 public long sendMany(UUID actor,List<UUID> ids,UUID templateId,Map<String,String> parameters){if(ids==null||ids.isEmpty()||ids.size()>1000)throw new ValidationException("userIds must contain 1..1000 users");NotificationTemplate t=templates.get(templateId);long sent=0;for(UUID id:new LinkedHashSet<>(ids)){if(!users.existsById(id))continue;try{notifications.send(id,t.type(),parameters==null?Map.of():parameters,ZoneId.of("UTC"),"admin:"+templateId+":"+clock.instant().toEpochMilli());sent++;}catch(RuntimeException ignored){}}audit.record(actor.toString(),"NOTIFICATIONS_SENT","NOTIFICATION_BATCH",templateId.toString(),Map.of("result","SUCCESS","requested",ids.size(),"sent",sent),clock.instant());return sent;}
 public AdminAuditPage audit(Integer page,Integer size,UUID actor,String action,Instant from,Instant to){int p=page==null?0:page,s=size==null?20:size;if(p<0||s<1||s>50||from!=null&&to!=null&&from.isAfter(to))throw new ValidationException("Invalid audit filter");return audit.list(p,s,actor,action,from,to);}
}
