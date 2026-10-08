package com.erudit.notification;

import com.erudit.events.*;
import com.erudit.web.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*; import java.util.*; import java.util.function.Function; import java.util.stream.Collectors;

@Service
public class UserNotificationService {
 private final UserNotificationRepository repository; private final NotificationTemplateService templates;
 private final NotificationPreferenceService preferences; private final Map<NotificationChannel,NotificationTransport> transports;
 private final ObjectProvider<AnalyticsEventPublisher> publisher; private final Clock clock;
 public UserNotificationService(UserNotificationRepository repository, NotificationTemplateService templates,
   NotificationPreferenceService preferences, List<NotificationTransport> transports,
   ObjectProvider<AnalyticsEventPublisher> publisher, Clock clock) {
  this.repository=repository;this.templates=templates;this.preferences=preferences;
  this.transports=transports.stream().collect(Collectors.toMap(NotificationTransport::channel,Function.identity()));
  this.publisher=publisher;this.clock=clock;
 }
 @Transactional
 public UserNotification send(UUID userId, NotificationType type, Map<String,String> parameters, ZoneId zone) {
  Instant now=clock.instant(); NotificationPreference pref=preferences.get(userId); RenderedNotification rendered=null;
  for(NotificationChannel channel: pref.channels()) if(pref.allows(channel,type,now.atZone(zone).toLocalTime())) {
   try { rendered=templates.render(type,channel,parameters); break; } catch(NotFoundException ignored) { }
  }
  if(rendered==null) throw new NotFoundException("No allowed notification template found");
  UserNotification value=new UserNotification(UUID.randomUUID(),userId,type,rendered.title(),rendered.body(),null,now);
  repository.insert(value);
  for(NotificationChannel channel: pref.channels()) {
   if(!pref.allows(channel,type,now.atZone(zone).toLocalTime())) continue;
   NotificationTransport transport=transports.get(channel); if(transport==null) continue;
   UUID deliveryId=repository.createDelivery(value.id(),channel,now);
   try {
    repository.updateDelivery(deliveryId,NotificationDeliveryStatus.SENT,now,null);
    transport.send(userId,value.title(),value.body());
    repository.updateDelivery(deliveryId,NotificationDeliveryStatus.DELIVERED,clock.instant(),null);
    publish(EventType.NOTIFICATION_DELIVERED,value);
   } catch(RuntimeException failure){
    repository.updateDelivery(deliveryId,NotificationDeliveryStatus.FAILED,clock.instant(),failure.getMessage());
   }
  }
  return value;
 }
 @Transactional(readOnly=true) public UserNotificationPage list(UUID user,Integer p,Integer s){int page=p==null?0:p,size=s==null?20:s;if(page<0||size<1||size>50)throw new ValidationException("page must be non-negative and size between 1 and 50");return new UserNotificationPage(repository.list(user,page,size),repository.count(user),page,size);}
 @Transactional public void read(UUID user,UUID id){UserNotification n=repository.find(id,user).orElseThrow(()->new NotFoundException("Notification not found"));if(repository.markRead(id,user,clock.instant()))publish(EventType.NOTIFICATION_OPENED,n);}
 @Transactional public void readAll(UUID user){repository.markAllRead(user,clock.instant());}
 @Transactional(readOnly=true) public long unread(UUID user){return repository.unread(user);}
 private void publish(EventType type,UserNotification n){AnalyticsEventPublisher p=publisher.getIfAvailable();if(p!=null)p.publish(new EventPublication(type,n.userId().toString(),"notification:"+n.id(),"{\"notificationId\":\""+n.id()+"\"}"));}
}

