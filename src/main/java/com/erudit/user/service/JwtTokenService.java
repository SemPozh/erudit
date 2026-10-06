package com.erudit.user.service;

import com.erudit.user.config.AuthProperties;
import com.erudit.user.domain.User;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class JwtTokenService {
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] secret;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtTokenService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
    }

    public String issueAccessToken(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTtl());
        String payload = ("{\"iss\":\"erudit\",\"sub\":\"%s\",\"email\":\"%s\","
                + "\"roles\":[\"USER\"],\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"}")
                .formatted(user.getId(), escape(user.getEmail()), issuedAt.getEpochSecond(),
                        expiresAt.getEpochSecond(), UUID.randomUUID());
        String content = encode(HEADER.getBytes(StandardCharsets.UTF_8)) + "."
                + encode(payload.getBytes(StandardCharsets.UTF_8));
        return content + "." + encode(sign(content));
    }

    public long accessTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign access token", exception);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
