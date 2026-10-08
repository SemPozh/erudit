package com.erudit.content;

import com.erudit.content.model.Category;
import com.erudit.content.model.Content;
import com.erudit.content.model.ContentProgress;
import com.erudit.content.model.ContentProgressStatus;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.model.ContentType;
import com.erudit.content.model.Difficulty;
import com.erudit.content.repository.ContentProgressRepository;
import com.erudit.content.repository.ContentRepository;

import com.erudit.events.service.AnalyticsEventPublisher;
import com.erudit.events.dto.EventPublication;
import com.erudit.events.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentProgressIntegrationTest {
    private static final String USER = "user-22";
    @Autowired private MockMvc mvc;
    @Autowired private ContentRepository contentRepository;
    @Autowired private ContentProgressRepository progressRepository;
    @MockitoBean private AnalyticsEventPublisher eventPublisher;
    private UUID contentId;

    @BeforeEach
    void setUp() {
        UUID categoryId = UUID.randomUUID();
        contentId = UUID.randomUUID();
        contentRepository.saveCategory(new Category(categoryId, "Progress " + categoryId));
        contentRepository.save(new Content(contentId, categoryId, ContentType.ARTICLE,
                "Progress article", "Description", "Body", null, Difficulty.BEGINNER,
                5, "author", ContentStatus.PUBLISHED, Instant.now(), List.of(), false));
        when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
    }

    @Test
    void persistsIdempotentProgressAndPublishesTransitionEvents() throws Exception {
        mvc.perform(get("/api/v1/content/{id}/progress", contentId).with(user(USER)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/content/{id}/view", contentId).with(user(USER))
                        .header("X-Session-Id", "session-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VIEWED"))
                .andExpect(jsonPath("$.data.viewedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());
        mvc.perform(post("/api/v1/content/{id}/view", contentId).with(user(USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("VIEWED"));

        mvc.perform(post("/api/v1/content/{id}/complete", contentId).with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());
        mvc.perform(post("/api/v1/content/{id}/complete", contentId).with(user(USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mvc.perform(get("/api/v1/content/{id}/progress", contentId).with(user(USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        verify(eventPublisher, times(2)).publish(argThat(event(EventType.CONTENT_VIEWED)));
        verify(eventPublisher).publish(argThat(event(EventType.CONTENT_STARTED)));
        verify(eventPublisher).publish(argThat(event(EventType.CONTENT_COMPLETED)));
        org.assertj.core.api.Assertions.assertThat(progressRepository.find(USER, contentId))
                .get().extracting(ContentProgress::status).isEqualTo(ContentProgressStatus.COMPLETED);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/content/{id}/view", contentId))
                .andExpect(status().isUnauthorized());
    }

    private org.mockito.ArgumentMatcher<EventPublication> event(EventType type) {
        return publication -> publication.eventType() == type
                && publication.userId().equals(USER)
                && publication.payload().contains(contentId.toString());
    }
}
