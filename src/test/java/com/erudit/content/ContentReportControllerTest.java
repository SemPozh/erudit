package com.erudit.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentReportController.class)
class ContentReportControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContentReportService service;

    @Test
    void acceptsReportRequest() throws Exception {
        UUID contentId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        String reason = "The article contains a factual error";
        when(service.report(contentId, reason))
                .thenReturn(new ContentReport(reportId, contentId, reason, Instant.now()));

        mvc.perform(post("/api/v1/content/{id}/report", contentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value(reportId.toString()));
        verify(service).report(contentId, reason);
    }
}
