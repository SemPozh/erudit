package com.erudit.user;

import com.erudit.content.model.Category;
import com.erudit.content.repository.ContentRepository;
import com.erudit.user.model.User;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.UserPreferencesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "clickhouse.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class UserSettingsPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private UserRepository userRepository;
    @Autowired private ContentRepository contentRepository;
    @Autowired private UserPreferencesService preferencesService;

    @Test
    void persistsSettingsAndFavoriteCategoriesOnPostgres() {
        User user = userRepository.save(User.builder()
                .email("postgres-settings-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").name("Postgres Settings").build());
        UUID categoryId = UUID.randomUUID();
        contentRepository.saveCategory(new Category(categoryId, "Postgres " + categoryId));

        preferencesService.update(user.getId(), "en-US", "Europe/London", List.of(categoryId),
                45, false, false, true);

        var reloaded = preferencesService.preferencesFor(user.getId());
        assertThat(reloaded.language()).isEqualTo("en-US");
        assertThat(reloaded.favoriteCategories()).containsExactly(categoryId);
        assertThat(reloaded.analyticsConsent()).isTrue();
    }
}
