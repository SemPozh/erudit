package com.erudit.notification;

import com.erudit.notification.model.NotificationChannel;
import com.erudit.notification.model.NotificationPreference;
import com.erudit.notification.model.NotificationType;
import com.erudit.notification.repository.NotificationPreferenceRepository;
import com.erudit.notification.service.NotificationPreferenceService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationPreferenceServiceTest {
    @Autowired private NotificationPreferenceService service;
    @Autowired private NotificationPreferenceRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void updatesPreferencesAndAppliesOvernightQuietHoursImmediately() {
        UUID userId = user();
        NotificationPreference updated = service.update(userId, List.of(NotificationChannel.PUSH),
                List.of(NotificationType.DAILY_QUIZ, NotificationType.SOCIAL), "22:00", "07:00");

        assertThat(repository.find(userId)).contains(updated);
        assertThat(service.allows(userId, NotificationChannel.PUSH, NotificationType.DAILY_QUIZ,
                Instant.parse("2026-10-06T21:59:00Z"), ZoneOffset.UTC)).isTrue();
        assertThat(service.allows(userId, NotificationChannel.PUSH, NotificationType.DAILY_QUIZ,
                Instant.parse("2026-10-06T23:00:00Z"), ZoneOffset.UTC)).isFalse();
        assertThat(service.allows(userId, NotificationChannel.EMAIL, NotificationType.DAILY_QUIZ,
                Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC)).isFalse();
    }

    @Test
    void returnsDefaultsAndRejectsIncompleteQuietHours() {
        UUID userId = user();
        assertThat(service.get(userId).channels()).containsExactlyInAnyOrder(NotificationChannel.values());
        assertThatThrownBy(() -> service.update(userId, List.of(NotificationChannel.PUSH),
                List.of(NotificationType.SOCIAL), "22:00", null))
                .isInstanceOf(com.erudit.web.exception.ValidationException.class);
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, name, status) VALUES (?, ?, ?, ?, ?)",
                id, id + "@example.test", "hash", "Notification user", "ACTIVE");
        return id;
    }
}
