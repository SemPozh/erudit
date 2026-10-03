package com.erudit.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "erudit.auth.verification")
public record VerificationProperties(
        String baseUrl,
        Duration tokenTtl
) {
    public VerificationProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8080";
        if (tokenTtl == null) tokenTtl = Duration.ofHours(24);
    }
}