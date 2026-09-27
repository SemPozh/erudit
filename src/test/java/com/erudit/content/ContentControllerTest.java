package com.erudit.content;

import com.erudit.openapi.model.ContentUpsertRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentController.class)
class ContentControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ContentService service;

    @Test
    void adminCreatesUpdatesAndArchivesContent() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content draft = content(contentId, categoryId, ContentStatus.DRAFT, "Initial");
        Content updated = content(contentId, categoryId, ContentStatus.DRAFT, "Updated");
        when(service.create(any(ContentUpsertRequest.class), eq("admin-1"))).thenReturn(draft);
        when(service.update(eq(contentId), any(ContentUpsertRequest.class))).thenReturn(updated);

        mvc.perform(post("/api/v1/content").with(this::admin)
                        .contentType(MediaType.APPLICATION_JSON).content(request(categoryId, "Initial")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(contentId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mvc.perform(put("/api/v1/content/{id}", contentId).with(this::admin)
                        .contentType(MediaType.APPLICATION_JSON).content(request(categoryId, "Updated")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Updated"));
        mvc.perform(delete("/api/v1/content/{id}", contentId).with(this::admin))
                .andExpect(status().isNoContent());
        verify(service).archive(contentId);
    }

    @Test
    void nonAdminCannotMutateContent() throws Exception {
        UUID categoryId = UUID.randomUUID();
        mvc.perform(post("/api/v1/content").contentType(MediaType.APPLICATION_JSON)
                        .content(request(categoryId, "Title")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void readsContentUsingCallerVisibility() throws Exception {
        UUID id = UUID.randomUUID();
        Content published = content(id, UUID.randomUUID(), ContentStatus.PUBLISHED, "Published");
        when(service.get(id, false)).thenReturn(published);
        mvc.perform(get("/api/v1/content/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Published"));
        verify(service).get(id, false);
    }

    @Test
    void validatesRequestAgainstOpenApiSchema() throws Exception {
        mvc.perform(post("/api/v1/content").with(this::admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + UUID.randomUUID() + "\",\"type\":\"UNKNOWN\",\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private org.springframework.mock.web.MockHttpServletRequest admin(
            org.springframework.mock.web.MockHttpServletRequest request) {
        request.addUserRole("ADMIN");
        request.setUserPrincipal(() -> "admin-1");
        return request;
    }

    private static String request(UUID categoryId, String title) {
        return "{\"categoryId\":\"" + categoryId + "\",\"type\":\"ARTICLE\",\"title\":\"" + title
                + "\",\"description\":\"Description\",\"body\":\"Body\",\"difficulty\":\"BEGINNER\"," 
                + "\"estimatedMinutes\":5,\"tags\":[\"science\"]}";
    }

    private static Content content(UUID id, UUID categoryId, ContentStatus status, String title) {
        return new Content(id, categoryId, ContentType.ARTICLE, title, "Description", "Body", null,
                Difficulty.BEGINNER, 5, "admin-1", status, Instant.now(), List.of("science"));
    }
}
