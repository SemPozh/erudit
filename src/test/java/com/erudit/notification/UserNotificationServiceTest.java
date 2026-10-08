package com.erudit.notification;

import com.erudit.events.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.time.*; import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserNotificationServiceTest {
 @Test void sendsPersistsAndPublishesDeliveryAndOpenEvents(){
  var repo=mock(UserNotificationRepository.class);var templates=mock(NotificationTemplateService.class);
  var prefs=mock(NotificationPreferenceService.class);var transport=mock(NotificationTransport.class);
  @SuppressWarnings("unchecked") ObjectProvider<AnalyticsEventPublisher> provider=mock(ObjectProvider.class);
  var publisher=mock(AnalyticsEventPublisher.class);UUID user=UUID.randomUUID();
  when(transport.channel()).thenReturn(NotificationChannel.EMAIL);
  when(provider.getIfAvailable()).thenReturn(publisher);
  when(repo.claim(eq(user),eq(NotificationType.SOCIAL),anyString(),any())).thenReturn(true);
  when(prefs.get(user)).thenReturn(new NotificationPreference(user,Set.of(NotificationChannel.EMAIL),Set.of(NotificationType.SOCIAL),null,null));
  when(templates.render(NotificationType.SOCIAL,NotificationChannel.EMAIL,Map.of("name","Ilya"))).thenReturn(new RenderedNotification("Hi","Hello"));
  UUID delivery=UUID.randomUUID();when(repo.createDelivery(any(),eq(NotificationChannel.EMAIL),any())).thenReturn(delivery);
  var service=new UserNotificationService(repo,templates,prefs,List.of(transport),provider,Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"),ZoneOffset.UTC));
  UserNotification sent=service.send(user,NotificationType.SOCIAL,Map.of("name","Ilya"),ZoneOffset.UTC);
  verify(repo).insert(sent);verify(transport).send(user,"Hi","Hello");verify(repo).updateDelivery(eq(delivery),eq(NotificationDeliveryStatus.SENT),any(),isNull());verify(repo).updateDelivery(eq(delivery),eq(NotificationDeliveryStatus.DELIVERED),any(),isNull());
  when(repo.find(sent.id(),user)).thenReturn(Optional.of(sent));when(repo.markRead(eq(sent.id()),eq(user),any())).thenReturn(true);
  service.read(user,sent.id());verify(publisher,times(2)).publish(any(EventPublication.class));
 }
 @Test void exposesHistoryUnreadAndReadAll(){var repo=mock(UserNotificationRepository.class);var service=new UserNotificationService(repo,mock(NotificationTemplateService.class),mock(NotificationPreferenceService.class),List.of(),mock(ObjectProvider.class),Clock.systemUTC());UUID u=UUID.randomUUID();when(repo.count(u)).thenReturn(0L);when(repo.list(u,0,20)).thenReturn(List.of());when(repo.unread(u)).thenReturn(3L);assertThat(service.list(u,0,20).items()).isEmpty();assertThat(service.unread(u)).isEqualTo(3);service.readAll(u);verify(repo).markAllRead(eq(u),any());}
 @Test void fallsBackFromPushToEmail(){var repo=mock(UserNotificationRepository.class);var templates=mock(NotificationTemplateService.class);var prefs=mock(NotificationPreferenceService.class);var push=mock(NotificationTransport.class);var email=mock(NotificationTransport.class);UUID u=UUID.randomUUID();when(push.channel()).thenReturn(NotificationChannel.PUSH);when(email.channel()).thenReturn(NotificationChannel.EMAIL);doThrow(new IllegalStateException("no token")).when(push).send(any(),any(),any());when(prefs.get(u)).thenReturn(new NotificationPreference(u,Set.of(NotificationChannel.PUSH,NotificationChannel.EMAIL),Set.of(NotificationType.SOCIAL),null,null));when(repo.claim(any(),any(),any(),any())).thenReturn(true);when(templates.render(eq(NotificationType.SOCIAL),any(),any())).thenReturn(new RenderedNotification("title","body"));var service=new UserNotificationService(repo,templates,prefs,List.of(push,email),mock(ObjectProvider.class),Clock.systemUTC());service.send(u,NotificationType.SOCIAL,Map.of(),ZoneOffset.UTC,"key");verify(email).send(u,"title","body");verify(repo).updateDelivery(any(),eq(NotificationDeliveryStatus.FAILED),any(),any());}
}
