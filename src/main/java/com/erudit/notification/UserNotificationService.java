package com.erudit.notification;

import com.erudit.events.*;
import com.erudit.web.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.*; import java.util.*; import java.util.function.Function; import java.util.stream.Collectors;

@Service
public class UserNotificationService {
 private final UserNotificationRepository repository; private final NotificationTemplateService templates;
 private final NotificationPreferenceService preferences; private final Map<NotificationChannel,NotificationTransport> transports;
 private final ObjectProvider<AnalyticsEventPublisher> publisher; private final Clock clock;
 private final int dailyLimit;
 @org.springframework.beans.factory.annotation.Autowired
 public UserNotificationService(UserNotificationRepository repository, NotificationTemplateService templates,
   NotificationPreferenceService preferences, List<NotificationTransport> transports,
   ObjectProvider<AnalyticsEventPublisher> publisher, Clock clock,
   @Value("${erudit.notifications.daily-limit:10}") int dailyLimit) {
  this.repository=repository;this.templates=templates;this.preferences=preferences;
  this.transports=transports.stream().collect(Collectors.toMap(NotificationTransport::channel,Function.identity()));
  this.publisher=publisher;this.clock=clock;this.dailyLimit=dailyLimit;
 }
 public UserNotificationService(UserNotificationRepository repository, NotificationTemplateService templates,
   NotificationPreferenceService preferences, List<NotificationTransport> transports,
   ObjectProvider<AnalyticsEventPublisher> publisher, Clock clock) {
  this(repository,templates,preferences,transports,publisher,clock,10);
 }
 @Transactional
 public UserNotification send(UUID userId, NotificationType type, Map<String,String> parameters, ZoneId zone) {
  return send(userId,type,parameters,zone,type+":"+new java.util.TreeMap<>(parameters));
 }
 @Transactional
 public UserNotification send(UUID userId, NotificationType type, Map<String,String> parameters, ZoneId zone,String deduplicationKey) {
  Instant now=clock.instant(); NotificationPreference pref=preferences.get(userId);
  if(repository.countSince(userId,now.minus(Duration.ofDays(1)))>=dailyLimit)throw new ValidationException("Daily notification limit reached");
  if(!repository.claim(userId,type,deduplicationKey,now))throw new ConflictException("Duplicate notification");
  RenderedNotification rendered=null;
  for(NotificationChannel channel: List.of(NotificationChannel.PUSH,NotificationChannel.EMAIL)) if(pref.allows(channel,type,now.atZone(zone).toLocalTime())) {rendered=render(type,channel,parameters);break;}
  if(rendered==null){repository.release(userId,type,deduplicationKey);throw new ValidationException("Notification is blocked by preferences or quiet hours");}
  UserNotification value=new UserNotification(UUID.randomUUID(),userId,type,rendered.title(),rendered.body(),null,now);
  repository.insert(value);
  for(NotificationChannel channel: List.of(NotificationChannel.PUSH,NotificationChannel.EMAIL)) {
   if(!pref.allows(channel,type,now.atZone(zone).toLocalTime())) continue;
   NotificationTransport transport=transports.get(channel); if(transport==null) continue;
   RenderedNotification channelMessage=render(type,channel,parameters);
   UUID deliveryId=repository.createDelivery(value.id(),channel,now);
   try {
    repository.updateDelivery(deliveryId,NotificationDeliveryStatus.SENT,now,null);
    transport.send(userId,channelMessage.title(),channelMessage.body());
    repository.updateDelivery(deliveryId,NotificationDeliveryStatus.DELIVERED,clock.instant(),null);
    publish(EventType.NOTIFICATION_DELIVERED,value);
    break;
   } catch(RuntimeException failure){
    repository.updateDelivery(deliveryId,NotificationDeliveryStatus.FAILED,clock.instant(),failure.getMessage());
   }
  }
  return value;
 }
 private RenderedNotification render(NotificationType type,NotificationChannel channel,Map<String,String> parameters){try{return templates.render(type,channel,parameters);}catch(NotFoundException missing){return new RenderedNotification(type.name().replace('_',' '),"You have a new "+type.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ')+" notification");}}
 @Transactional(readOnly=true) public UserNotificationPage list(UUID user,Integer p,Integer s){int page=p==null?0:p,size=s==null?20:s;if(page<0||size<1||size>50)throw new ValidationException("page must be non-negative and size between 1 and 50");return new UserNotificationPage(repository.list(user,page,size),repository.count(user),page,size);}
 @Transactional public void read(UUID user,UUID id){UserNotification n=repository.find(id,user).orElseThrow(()->new NotFoundException("Notification not found"));if(repository.markRead(id,user,clock.instant()))publish(EventType.NOTIFICATION_OPENED,n);}
 @Transactional public void readAll(UUID user){repository.markAllRead(user,clock.instant());}
 @Transactional(readOnly=true) public long unread(UUID user){return repository.unread(user);}
 private void publish(EventType type,UserNotification n){AnalyticsEventPublisher p=publisher.getIfAvailable();if(p!=null)p.publish(new EventPublication(type,n.userId().toString(),"notification:"+n.id(),"{\"notificationId\":\""+n.id()+"\"}"));}
}

