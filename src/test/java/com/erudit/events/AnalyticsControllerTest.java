package com.erudit.events;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AnalyticsService service;

    @Test
    void returnsActiveUsersAndSessionsAsMetricSeries() throws Exception {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-03T00:00:00Z");
        when(service.activeUsers(eq(from), eq(to), eq("DAY"))).thenReturn(new AnalyticsMetricSeries(
                "dau", List.of(new AnalyticsMetricPoint(from, 3))));
        when(service.sessions(eq(from), eq(to), eq("DAY"))).thenReturn(new AnalyticsMetricSeries(
                "sessions", List.of(new AnalyticsMetricPoint(from, 5))));

        mvc.perform(get("/api/v1/analytics/active-users")
                        .param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-03T00:00:00Z")
                        .with(user("analyst")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.metric").value("dau"))
                .andExpect(jsonPath("$.data.points[0].value").value(3.0));
        mvc.perform(get("/api/v1/analytics/sessions")
                        .param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-03T00:00:00Z")
                        .with(user("analyst")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.metric").value("sessions"))
                .andExpect(jsonPath("$.data.points[0].value").value(5.0));
    }
}

