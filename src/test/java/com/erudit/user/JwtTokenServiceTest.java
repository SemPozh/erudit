package com.erudit.user;

import com.erudit.user.config.AuthProperties;
import com.erudit.user.domain.User;
import com.erudit.user.service.JwtTokenService;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {
    private static final String SECRET = "a-test-secret-that-is-at-least-32-bytes-long";

    @Test
    void issuesSignedAccessTokenWithIdentityAndExpiry() throws Exception {
        Instant now = Instant.parse("2026-10-06T10:00:00Z");
        JwtTokenService service = new JwtTokenService(
                new AuthProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(30)),
                Clock.fixed(now, ZoneOffset.UTC));
        User user = User.builder().id(UUID.fromString("10000000-0000-0000-0000-000000000001"))
                .email("student@example.com").build();

        String token = service.issueAccessToken(user);
        String[] parts = token.split("\\.");

        assertThat(parts).hasSize(3);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"10000000-0000-0000-0000-000000000001\"")
                .contains("\"email\":\"student@example.com\"")
                .contains("\"iat\":1791280800")
                .contains("\"exp\":1791281700");

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(Base64.getUrlDecoder().decode(parts[2])).isEqualTo(expected);
    }
}
