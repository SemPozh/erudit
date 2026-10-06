package com.erudit.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("erudit.auth.password-reset")
public record PasswordResetProperties(String baseUrl, Duration tokenTtl) {
    public PasswordResetProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8080";
        if (tokenTtl == null) tokenTtl = Duration.ofHours(1);
    }
}
