package com.erudit.quiz;

import com.erudit.aiplatform.client.AiClient;
import com.erudit.notificator.client.NotificationClient;
import com.erudit.user.client.UserClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(clients = {UserClient.class, NotificationClient.class, AiClient.class})
public class ServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }
}
