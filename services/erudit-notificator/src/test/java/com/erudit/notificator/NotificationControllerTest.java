package com.erudit.notificator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erudit.notificator.controller.InternalNotificationController;
import com.erudit.notificator.controller.NotificationController;
import com.erudit.notificator.controller.TemplateController;
import com.erudit.notificator.model.NotificationRecord;
import com.erudit.notificator.model.Preference;
import com.erudit.notificator.model.Template;
import com.erudit.notificator.openapi.model.EnqueueRequest;
import com.erudit.notificator.openapi.model.PreferenceRequest;
import com.erudit.notificator.openapi.model.TemplateRequest;
import com.erudit.notificator.service.NotificationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class NotificationControllerTest {
    private final NotificationService service = mock(NotificationService.class);
    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private NotificationRecord notification;
    private Template template;

    @BeforeEach
    void setUp() {
        notification = new NotificationRecord(notificationId, userId, "DEMO", "Title", "Body", false,
                OffsetDateTime.now());
        template = new Template(templateId, "DEMO", "Title", "Body", true);
    }

    @Test
    void delegatesPreferenceAndInboxOperations() {
        var controller = new NotificationController(service);
        var preference = new Preference(userId, true, List.of("IN_APP"));
        when(service.preference(userId)).thenReturn(Mono.just(preference));
        when(service.savePreference(any())).thenReturn(Mono.just(preference));
        when(service.list(userId, 0, 20)).thenReturn(Mono.just(new NotificationService.Page(List.of(notification), 1, 0, 20)));
        when(service.read(userId, notificationId)).thenReturn(Mono.empty());
        when(service.readAll(userId)).thenReturn(Mono.empty());
        when(service.unread(userId)).thenReturn(Mono.just(1L));

        assertThat(controller.getPreferences(userId, null).block().getBody().getUserId()).isEqualTo(userId);
        var request = new PreferenceRequest(true, List.of(PreferenceRequest.ChannelsEnum.IN_APP));
        assertThat(controller.updatePreferences(Mono.just(request), userId, null).block().getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(controller.listNotifications(userId, 0, 20, null).block().getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(controller.markRead(notificationId, userId, null).block().getStatusCode().value()).isEqualTo(204);
        assertThat(controller.markAllRead(userId, null).block().getStatusCode().value()).isEqualTo(204);
        assertThat(controller.unreadCount(userId, null).block().getBody().getCount()).isEqualTo(1L);
    }

    @Test
    void delegatesTemplateCrud() {
        var controller = new TemplateController(service);
        var request = new TemplateRequest("DEMO", "Title", "Body", true);
        when(service.saveTemplate(any())).thenReturn(Mono.just(template));
        when(service.templates()).thenReturn(Flux.just(template));
        when(service.template(templateId)).thenReturn(Mono.just(template));
        when(service.updateTemplate(any())).thenReturn(Mono.just(template));
        when(service.deleteTemplate(templateId)).thenReturn(Mono.empty());

        assertThat(controller.createTemplate(Mono.just(request), null).block().getStatusCode().value()).isEqualTo(201);
        StepVerifier.create(controller.listTemplates(null).block().getBody()).expectNextCount(1).verifyComplete();
        assertThat(controller.getTemplate(templateId, null).block().getBody().getId()).isEqualTo(templateId);
        assertThat(controller.updateTemplate(templateId, Mono.just(request), null).block().getBody().getCode()).isEqualTo("DEMO");
        assertThat(controller.deleteTemplate(templateId, null).block().getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void delegatesSingleAndBatchEnqueue() {
        var controller = new InternalNotificationController(service);
        var request = new EnqueueRequest(userId, "DEMO").title("Title").body("Body");
        when(service.enqueue(userId, "DEMO", "Title", "Body")).thenReturn(Mono.just(notification));
        when(service.enqueue(any(List.class))).thenReturn(Flux.just(notification));

        assertThat(controller.enqueue(Mono.just(request), null).block().getStatusCode().value()).isEqualTo(202);
        StepVerifier.create(controller.enqueueBatch(Flux.just(request), null).block().getBody())
                .expectNextCount(1)
                .verifyComplete();
    }
}
