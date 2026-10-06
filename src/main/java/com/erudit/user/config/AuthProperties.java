package com.erudit.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("erudit.auth.jwt")
public record AuthProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
