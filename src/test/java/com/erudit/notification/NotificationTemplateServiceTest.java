package com.erudit.notification;

import com.erudit.notification.model.NotificationChannel;
import com.erudit.notification.model.NotificationTemplate;
import com.erudit.notification.model.NotificationType;
import com.erudit.notification.model.RenderedNotification;
import com.erudit.notification.service.NotificationTemplateService;
import com.erudit.quiz.model.Quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationTemplateServiceTest {
    @Autowired private NotificationTemplateService service;

    @Test
    void storesUpdatesAndRendersParameterizedTemplate() {
        NotificationTemplate created = service.create("DAILY_QUIZ", "PUSH", "Quiz for {{name}}",
                "Hello, {{name}}. Your quiz has {{count}} questions.");

        assertThat(created.parameters()).containsExactlyInAnyOrder("name", "count");
        assertThat(service.list(0, 20).items()).extracting(NotificationTemplate::id).contains(created.id());
        RenderedNotification rendered = service.render(NotificationType.DAILY_QUIZ, NotificationChannel.PUSH,
                Map.of("name", "Ilya", "count", "10"));
        assertThat(rendered.title()).isEqualTo("Quiz for Ilya");
        assertThat(rendered.body()).isEqualTo("Hello, Ilya. Your quiz has 10 questions.");

        NotificationTemplate updated = service.update(created.id(), "DAILY_QUIZ", "PUSH", null,
                "A new quiz for {{name}}");
        assertThat(updated.parameters()).containsExactly("name");
    }

    @Test
    void rejectsDuplicateKeyMissingParametersAndMalformedPlaceholder() {
        service.create("SOCIAL", "EMAIL", "Friend update", "{{friend}} sent an update");
        assertThatThrownBy(() -> service.create("SOCIAL", "EMAIL", null, "Duplicate"))
                .isInstanceOf(com.erudit.web.exception.ConflictException.class);
        assertThatThrownBy(() -> service.render(NotificationType.SOCIAL, NotificationChannel.EMAIL, Map.of()))
                .isInstanceOf(com.erudit.web.exception.ValidationException.class)
                .hasMessageContaining("friend");
        assertThatThrownBy(() -> service.create("ACHIEVEMENT", "PUSH", null, "Broken {{name}"))
                .isInstanceOf(com.erudit.web.exception.ValidationException.class);
    }
}
