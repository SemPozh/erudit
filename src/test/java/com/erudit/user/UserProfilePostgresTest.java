package com.erudit.user;

import com.erudit.media.MediaAsset;
import com.erudit.media.MediaRepository;
import com.erudit.user.domain.User;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "clickhouse.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class UserProfilePostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private UserRepository userRepository;
    @Autowired private MediaRepository mediaRepository;
    @Autowired private UserProfileService profileService;

    @Test
    void persistsProfileAndAvatarReferenceOnPostgres() {
        User user = userRepository.save(User.builder()
                .email("postgres-profile-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").name("Old Name").build());
        UUID avatarId = UUID.randomUUID();
        mediaRepository.save(new MediaAsset(avatarId, "profile/" + avatarId, "avatar.webp",
                "image/webp", 256, Instant.now()));

        profileService.update(user.getId(), "New Name", avatarId);

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("New Name");
        assertThat(reloaded.getAvatar()).isEqualTo("/api/v1/media/" + avatarId);
    }
}
