package com.erudit.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private NotificationPreferenceService service;

    @Test
    void readsAndUpdatesCurrentUsersPreferences() throws Exception {
        UUID userId = UUID.randomUUID();
        NotificationPreference value = new NotificationPreference(userId, Set.of(NotificationChannel.PUSH),
                Set.of(NotificationType.DAILY_QUIZ), LocalTime.of(22, 0), LocalTime.of(7, 0));
        when(service.get(userId)).thenReturn(value);
        when(service.update(userId, java.util.List.of(NotificationChannel.PUSH),
                java.util.List.of(NotificationType.DAILY_QUIZ),
                "22:00", "07:00")).thenReturn(value);

        mvc.perform(get("/api/v1/notifications/preferences").with(request -> authenticated(request, userId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.channels[0]").value("PUSH"))
                .andExpect(jsonPath("$.data.quietHoursStart").value("22:00"));
        mvc.perform(put("/api/v1/notifications/preferences").with(request -> authenticated(request, userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channels\":[\"PUSH\"],\"types\":[\"DAILY_QUIZ\"],"
                                + "\"quietHoursStart\":\"22:00\",\"quietHoursEnd\":\"07:00\"}"))
                .andExpect(status().isOk());
        verify(service).update(userId, java.util.List.of(NotificationChannel.PUSH),
                java.util.List.of(NotificationType.DAILY_QUIZ),
                "22:00", "07:00");
    }

    @Test
    void requiresAuthenticationAndValidatesContract() throws Exception {
        mvc.perform(get("/api/v1/notifications/preferences")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/notifications/preferences")
                        .with(request -> authenticated(request, UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channels\":[\"SMS\"],\"types\":[]}"))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.mock.web.MockHttpServletRequest authenticated(
            org.springframework.mock.web.MockHttpServletRequest request, UUID userId) {
        request.setUserPrincipal(userId::toString);
        return request;
    }
}
