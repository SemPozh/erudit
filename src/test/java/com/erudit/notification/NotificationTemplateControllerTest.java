package com.erudit.notification;

import com.erudit.notification.controller.NotificationTemplateController;
import com.erudit.notification.dto.NotificationTemplatePage;
import com.erudit.notification.model.NotificationChannel;
import com.erudit.notification.model.NotificationTemplate;
import com.erudit.notification.model.NotificationType;
import com.erudit.notification.service.NotificationTemplateService;
import com.erudit.quiz.model.Quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationTemplateController.class)
class NotificationTemplateControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private NotificationTemplateService service;

    @Test
    void adminCreatesListsAndUpdatesTemplates() throws Exception {
        UUID id = UUID.randomUUID();
        NotificationTemplate value = new NotificationTemplate(id, NotificationType.DAILY_QUIZ,
                NotificationChannel.PUSH, "Quiz", "Hello {{name}}", Set.of("name"));
        when(service.create("DAILY_QUIZ", "PUSH", "Quiz", "Hello {{name}}")).thenReturn(value);
        when(service.list(0, 20)).thenReturn(new NotificationTemplatePage(List.of(value), 1, 0, 20));
        when(service.update(eq(id), eq("DAILY_QUIZ"), eq("PUSH"), eq("Quiz"), eq("Hello {{name}}")))
                .thenReturn(value);
        String request = "{\"type\":\"DAILY_QUIZ\",\"channel\":\"PUSH\","
                + "\"title\":\"Quiz\",\"body\":\"Hello {{name}}\"}";

        mvc.perform(post("/api/v1/notifications/templates").with(this::admin)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.parameters[0]").value("name"));
        mvc.perform(get("/api/v1/notifications/templates").with(this::admin))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"));
        mvc.perform(put("/api/v1/notifications/templates/{id}", id).with(this::admin)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminCannotManageTemplates() throws Exception {
        mvc.perform(get("/api/v1/notifications/templates"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.mock.web.MockHttpServletRequest admin(
            org.springframework.mock.web.MockHttpServletRequest request) {
        request.addUserRole("ADMIN");
        request.setUserPrincipal(() -> "admin");
        return request;
    }
}
