package com.erudit.user.service;

import com.erudit.user.domain.User;
import com.erudit.user.domain.UserPreferences;
import com.erudit.user.repository.UserPreferencesRepository;
import com.erudit.user.repository.UserRepository;
import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserPreferencesService implements UserPreferenceProvider {
    private final UserPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public UserPreferenceSnapshot preferencesFor(UUID userId) {
        return snapshot(findOrCreate(userId));
    }

    @Transactional
    public UserPreferenceSnapshot update(UUID userId, String language, String timeZone,
                                         Collection<UUID> favoriteCategories, int dailyGoalMinutes,
                                         boolean visibleInSearch, boolean visibleInRating,
                                         boolean analyticsConsent) {
        String normalizedLanguage = validateLanguage(language);
        String normalizedTimeZone = validateTimeZone(timeZone);
        if (dailyGoalMinutes < 1 || dailyGoalMinutes > 1440) {
            throw new ValidationException("Daily goal must be between 1 and 1440 minutes");
        }
        if (favoriteCategories == null) {
            throw new ValidationException("Favorite categories are required");
        }
        Set<UUID> categories = new LinkedHashSet<>(favoriteCategories);
        if (categories.size() != favoriteCategories.size() || categories.size() > 50) {
            throw new ValidationException("Favorite categories must be unique and contain at most 50 items");
        }
        validateCategories(categories);

        UserPreferences preferences = findOrCreate(userId);
        preferences.setLanguage(normalizedLanguage);
        preferences.setTimeZone(normalizedTimeZone);
        preferences.setFavoriteCategories(categories);
        preferences.setDailyGoalMinutes(dailyGoalMinutes);
        preferences.setVisibleInSearch(visibleInSearch);
        preferences.setVisibleInRating(visibleInRating);
        preferences.setAnalyticsConsent(analyticsConsent);
        return snapshot(preferencesRepository.save(preferences));
    }

    private UserPreferences findOrCreate(UUID userId) {
        return preferencesRepository.findById(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));
            return preferencesRepository.save(UserPreferences.builder().user(user).build());
        });
    }

    private void validateCategories(Set<UUID> categories) {
        if (categories.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(categories.size(), "?"));
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE id IN (" + placeholders + ")",
                Integer.class, categories.toArray());
        if (found == null || found != categories.size()) {
            throw new ValidationException("One or more favorite categories do not exist");
        }
    }

    private static String validateLanguage(String value) {
        if (value == null || !value.matches("[A-Za-z]{2,3}(-[A-Za-z]{2})?")) {
            throw new ValidationException("Language must be a valid language tag");
        }
        return Locale.forLanguageTag(value).toLanguageTag();
    }

    private static String validateTimeZone(String value) {
        try {
            return ZoneId.of(value).getId();
        } catch (DateTimeException | NullPointerException exception) {
            throw new ValidationException("Time zone must be a valid IANA identifier");
        }
    }

    private static UserPreferenceSnapshot snapshot(UserPreferences value) {
        return new UserPreferenceSnapshot(value.getLanguage(), value.getTimeZone(),
                Set.copyOf(value.getFavoriteCategories()), value.getDailyGoalMinutes(),
                value.isVisibleInSearch(), value.isVisibleInRating(), value.isAnalyticsConsent());
    }
}
