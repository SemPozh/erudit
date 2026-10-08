package com.erudit.user;

import com.erudit.user.model.RefreshToken;
import com.erudit.user.model.User;
import com.erudit.user.repository.RefreshTokenRepository;
import com.erudit.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "clickhouse.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @Test
    void persistsRefreshTokenAndRevocationOnPostgres() {
        User user = userRepository.save(User.builder()
                .email("postgres-auth-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").name("Postgres Auth").build());
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                .user(user).tokenHash("a".repeat(64))
                .createdAt(now).expiresAt(now.plusDays(30)).build());

        token.setRevokedAt(now.plusMinutes(1));
        refreshTokenRepository.saveAndFlush(token);

        assertThat(refreshTokenRepository.findByTokenHash("a".repeat(64)))
                .get().extracting(RefreshToken::getRevokedAt).isNotNull();
    }
}
