package com.erudit.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuthConfiguration {
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }
}
