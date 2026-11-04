package com.erudit.admin.client;
import feign.Retryer;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
@EnableFeignClients(clients={RemoteClients.Users.class,RemoteClients.Content.class,RemoteClients.Notifications.class,RemoteClients.Payments.class})
public class AdminClientConfiguration {
    @Bean Retryer adminNoRetry(){return Retryer.NEVER_RETRY;}
}
