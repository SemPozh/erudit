package com.erudit.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentModerationController.class)
class ContentModerationControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ContentService service;

    @Test
    void adminListsApprovesAndRejectsPendingContent() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Content first = content(firstId, ContentStatus.PENDING_MODERATION);
        Content second = content(secondId, ContentStatus.PENDING_MODERATION);
        when(service.moderationQueue(0, 20)).thenReturn(new ContentPage(List.of(first, second), 2, 0, 20));
        when(service.moderate(firstId, ContentStatus.PUBLISHED)).thenReturn(content(firstId, ContentStatus.PUBLISHED));
        when(service.moderate(secondId, ContentStatus.REJECTED)).thenReturn(content(secondId, ContentStatus.REJECTED));

        mvc.perform(get("/api/v1/admin/content/moderation").param("page", "0").param("size", "20")
                        .with(this::admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(post("/api/v1/admin/content/{id}/approve", firstId)
                        .with(this::admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mvc.perform(post("/api/v1/admin/content/{id}/reject", secondId)
                        .with(this::admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void nonAdminCannotModerate() throws Exception {
        mvc.perform(get("/api/v1/admin/content/moderation"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.mock.web.MockHttpServletRequest admin(
            org.springframework.mock.web.MockHttpServletRequest request) {
        request.addUserRole("ADMIN");
        request.setUserPrincipal(() -> "admin");
        return request;
    }

    private static Content content(UUID id, ContentStatus status) {
        return new Content(id, UUID.randomUUID(), ContentType.ARTICLE, "Title", "Description", "Body", null,
                Difficulty.BEGINNER, 5, "author", status, Instant.parse("2026-10-06T12:00:00Z"),
                List.of(), false);
    }
}
