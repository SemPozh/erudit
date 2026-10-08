package com.erudit.content;

import com.erudit.content.controller.ContentModerationController;
import com.erudit.content.dto.ContentPage;
import com.erudit.content.dto.ContentReportPage;
import com.erudit.content.model.Content;
import com.erudit.content.model.ContentReport;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.model.ContentType;
import com.erudit.content.model.Difficulty;
import com.erudit.content.model.ReportDecision;
import com.erudit.content.service.ContentReportService;
import com.erudit.content.service.ContentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentModerationController.class)
class ContentModerationControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ContentService service;
    @MockitoBean private ContentReportService reportService;

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

    @Test
    void adminListsAndResolvesReports() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        ContentReport report = new ContentReport(reportId, contentId, "Incorrect fact",
                Instant.parse("2026-10-06T12:00:00Z"));
        when(reportService.openReports(0, 20))
                .thenReturn(new ContentReportPage(List.of(report), 1, 0, 20));

        mvc.perform(get("/api/v1/admin/content/reports").param("page", "0").param("size", "20")
                        .with(this::admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(reportId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"));
        mvc.perform(post("/api/v1/admin/content/reports/{id}/resolve", reportId).with(this::admin)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\",\"comment\":\"Confirmed\"}"))
                .andExpect(status().isNoContent());
        verify(reportService).resolve(reportId, ReportDecision.ACCEPT, "Confirmed", "admin");
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
