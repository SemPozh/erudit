package com.erudit.notification.model;

import com.erudit.notification.repository.NotificationTriggerRepository;
import com.erudit.notification.service.UserNotificationService;
import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;import java.time.*;import java.util.*;
@Component public class NotificationTriggerScheduler {
 private final NotificationTriggerRepository targets;private final UserNotificationService notifications;private final Clock clock;
 public NotificationTriggerScheduler(NotificationTriggerRepository targets,UserNotificationService notifications,Clock clock){this.targets=targets;this.notifications=notifications;this.clock=clock;}
 @Scheduled(cron="${erudit.notifications.trigger-cron:0 0 * * * *}") public void run(){Instant now=clock.instant();dispatch(NotificationType.INACTIVITY,targets.inactive(now));dispatch(NotificationType.DAILY_QUIZ,targets.dailyQuiz(now));dispatch(NotificationType.NEW_CONTENT,targets.newContent(now.minus(Duration.ofHours(2))));dispatch(NotificationType.ACHIEVEMENT,targets.achievements());dispatch(NotificationType.SUBSCRIPTION,targets.subscriptions(now));}
 void dispatch(NotificationType type,List<NotificationTriggerRepository.Target> values){for(var t:values)try{notifications.send(t.userId(),type,t.parameters(),t.zone(),t.key());}catch(RuntimeException ignored){}}
}
