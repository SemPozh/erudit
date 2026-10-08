package com.erudit.user;

import com.erudit.user.repository.UserPreferencesRepository;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.UserPreferencesService;
import com.erudit.web.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class UserPreferencesServiceTest {
    private final UserPreferencesService service = new UserPreferencesService(
            mock(UserPreferencesRepository.class), mock(UserRepository.class), mock(JdbcTemplate.class));

    @Test
    void rejectsInvalidLocaleAndLearningGoalBeforePersistence() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.update(userId, "invalid_language", "UTC", List.of(),
                15, true, true, false)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.update(userId, "ru", "Mars/Olympus", List.of(),
                15, true, true, false)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.update(userId, "ru", "UTC", List.of(),
                0, true, true, false)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsDuplicateFavoriteCategories() {
        UUID categoryId = UUID.randomUUID();
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), "ru", "UTC",
                List.of(categoryId, categoryId), 15, true, true, false))
                .isInstanceOf(ValidationException.class);
    }
}
