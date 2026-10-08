package com.erudit.user;

import com.erudit.content.model.Category;
import com.erudit.content.repository.ContentRepository;
import com.erudit.user.model.User;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.JwtTokenService;
import com.erudit.user.service.UserPreferenceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSettingsIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ContentRepository contentRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private UserPreferenceProvider preferenceProvider;

    private User user;
    private String accessToken;

    @BeforeEach
    void createUser() {
        user = userRepository.save(User.builder()
                .email("settings-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").name("Settings User").build());
        accessToken = jwtTokenService.issueAccessToken(user);
    }

    @Test
    void returnsDefaultsAndImmediatelyPublishesUpdatedPreferences() throws Exception {
        mvc.perform(get("/api/v1/users/me/settings").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language").value("ru"))
                .andExpect(jsonPath("$.data.timeZone").value("UTC"))
                .andExpect(jsonPath("$.data.dailyGoalMinutes").value(15))
                .andExpect(jsonPath("$.data.analyticsConsent").value(false));

        UUID categoryId = UUID.randomUUID();
        contentRepository.saveCategory(new Category(categoryId, "Settings " + categoryId));
        mvc.perform(put("/api/v1/users/me/settings").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(request(categoryId, "Europe/Moscow", 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoriteCategories[0]").value(categoryId.toString()))
                .andExpect(jsonPath("$.data.visibleInSearch").value(false))
                .andExpect(jsonPath("$.data.analyticsConsent").value(true));

        var current = preferenceProvider.preferencesFor(user.getId());
        assertThat(current.favoriteCategories()).containsExactly(categoryId);
        assertThat(current.analyticsConsent()).isTrue();
        assertThat(current.visibleInSearch()).isFalse();
    }

    @Test
    void rejectsUnknownCategoriesAndInvalidTimeZone() throws Exception {
        mvc.perform(put("/api/v1/users/me/settings").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID(), "UTC", 20)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/users/me/settings").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithoutCategories("Invalid/Zone", 20)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/me/settings")).andExpect(status().isUnauthorized());
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }

    private static String request(UUID categoryId, String timeZone, int goal) {
        return """
                {"language":"ru-RU","timeZone":"%s","favoriteCategories":["%s"],
                 "dailyGoalMinutes":%d,"visibleInSearch":false,"visibleInRating":true,
                 "analyticsConsent":true}
                """.formatted(timeZone, categoryId, goal);
    }

    private static String requestWithoutCategories(String timeZone, int goal) {
        return """
                {"language":"ru","timeZone":"%s","favoriteCategories":[],
                 "dailyGoalMinutes":%d,"visibleInSearch":true,"visibleInRating":true,
                 "analyticsConsent":false}
                """.formatted(timeZone, goal);
    }
}
