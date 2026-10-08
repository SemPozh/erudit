package com.erudit.user;

import com.erudit.content.model.Category;
import com.erudit.content.model.Content;
import com.erudit.content.repository.ContentRepository;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.model.ContentType;
import com.erudit.content.model.Difficulty;
import com.erudit.events.service.AnalyticsEventPublisher;
import com.erudit.user.model.User;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsConsentIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ContentRepository contentRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @MockitoBean private AnalyticsEventPublisher publisher;

    @Test
    void publishesPersonalAnalyticsOnlyAfterConsent() throws Exception {
        User user = userRepository.save(User.builder()
                .email("consent-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").name("Consent User").build());
        String bearer = "Bearer " + jwtTokenService.issueAccessToken(user);
        UUID categoryId = UUID.randomUUID();
        contentRepository.saveCategory(new Category(categoryId, "Consent " + categoryId));
        UUID firstContent = content(categoryId, "First");

        mvc.perform(post("/api/v1/content/{id}/view", firstContent)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        verify(publisher, never()).publish(any());

        mvc.perform(put("/api/v1/users/me/settings").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"ru","timeZone":"UTC","favoriteCategories":[],
                                 "dailyGoalMinutes":15,"visibleInSearch":true,"visibleInRating":true,
                                 "analyticsConsent":true}
                                """))
                .andExpect(status().isOk());
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
        UUID secondContent = content(categoryId, "Second");

        mvc.perform(post("/api/v1/content/{id}/view", secondContent)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        verify(publisher, org.mockito.Mockito.times(2)).publish(any());
    }

    private UUID content(UUID categoryId, String suffix) {
        UUID id = UUID.randomUUID();
        contentRepository.save(new Content(id, categoryId, ContentType.ARTICLE,
                "Consent " + suffix, "Description", "Body", null, Difficulty.BEGINNER,
                5, "author", ContentStatus.PUBLISHED, Instant.now(), List.of(), false));
        return id;
    }
}
