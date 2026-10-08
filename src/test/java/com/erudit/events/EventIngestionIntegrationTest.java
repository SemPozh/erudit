package com.erudit.events;

import com.erudit.events.model.AnalyticsEvent;
import com.erudit.events.service.AnalyticsEventSink;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventIngestionIntegrationTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AnalyticsEventSink sink;

    @Test
    void acceptsSingleAndBatchEventsAndDeduplicatesByEventId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String event = event(eventId, userId, "content_viewed");

        mvc.perform(post("/api/v1/events").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.data.accepted").value(true));
        mvc.perform(post("/api/v1/events").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.accepted").value(false));

        String batch = "{\"events\":[" + event(UUID.randomUUID(), userId, "content_started")
                + "," + event(UUID.randomUUID(), userId, "content_completed") + "]}";
        mvc.perform(post("/api/v1/events/batch").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].accepted").value(true))
                .andExpect(jsonPath("$.data[1].accepted").value(true));

        verify(sink, times(3)).write(any(AnalyticsEvent.class));
    }

    @Test
    void validatesEventTypeOwnershipAndAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        String invalid = event(UUID.randomUUID(), userId, "unknown_event");
        mvc.perform(post("/api/v1/events").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/events").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event(UUID.randomUUID(), UUID.randomUUID(), "content_viewed")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                        .content(event(UUID.randomUUID(), userId, "content_viewed")))
                .andExpect(status().isUnauthorized());
    }

    private static String event(UUID eventId, UUID userId, String type) {
        return """
                {"eventId":"%s","userId":"%s","sessionId":"session-1",
                 "eventType":"%s","timestamp":"%s","payload":{"contentId":"item-1"}}
                """.formatted(eventId, userId, type, Instant.now());
    }
}
