package com.erudit.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, PasswordResetProperties.class})
public class AuthConfiguration {
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }
}
