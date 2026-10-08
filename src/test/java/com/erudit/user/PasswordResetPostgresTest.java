package com.erudit.user;

import com.erudit.user.model.User;
import com.erudit.user.service.EmailSender;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "clickhouse.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class PasswordResetPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailSender emailSender;

    @Test
    void storesAndConsumesResetTokenOnPostgres() {
        String email = "postgres-reset-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(User.builder().email(email)
                .passwordHash(passwordEncoder.encode("old-password"))
                .name("Postgres Reset").build());
        passwordResetService.request(email);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendPasswordResetEmail(anyString(), anyString(), link.capture());
        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        passwordResetService.reset(rawToken, "new-password");

        assertThat(passwordEncoder.matches("new-password",
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
}
