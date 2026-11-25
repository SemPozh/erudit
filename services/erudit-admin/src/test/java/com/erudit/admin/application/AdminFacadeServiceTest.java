package com.erudit.admin.application;

import com.erudit.admin.application.AdminModels.*;
import com.erudit.admin.application.AdminOperationRunner.Context;
import com.erudit.admin.application.port.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminFacadeServiceTest {
    private final AdminRemote.Users users = mock(AdminRemote.Users.class);
    private final AdminRemote.Content content = mock(AdminRemote.Content.class);
    private final AdminRemote.Notifications notifications = mock(AdminRemote.Notifications.class);
    private final AdminRemote.Payments payments = mock(AdminRemote.Payments.class);
    private final AuditLog audit = mock(AuditLog.class);
    private final AdminFacadeService service = new AdminFacadeService(users, content, notifications, payments,
            new AdminOperationRunner(audit), mock(AdminAuditRepository.class));
    private final Context context = new Context(UUID.randomUUID(), "task-109");

    @Test void duplicateRecipientsAreQueuedOnceAndAudited() {
        UUID user = UUID.randomUUID(), template = UUID.randomUUID(), auditId = UUID.randomUUID();
        when(audit.start(any(), any(), any(), any(), any())).thenReturn(auditId);
        when(notifications.template(template, context.requestId()))
                .thenReturn(new AdminRemote.Template(template, "CODE", "Hi {{name}}", "Body", true));
        when(users.get(user, context.requestId())).thenReturn(new AdminRemote.UserState(user, "User", true));
        when(notifications.enqueue(any(), any())).thenReturn(new AdminRemote.NotificationAck(UUID.randomUUID(), user));
        assertThat(service.sendMany(context, new SendMany(List.of(user, user), template, Map.of("name", "Student")))).isOne();
        verify(notifications).enqueue(new AdminRemote.Enqueue(user, "CODE", "Hi Student", "Body"), context.requestId());
        verify(audit).finish(auditId, "SUCCESS", Map.of("sent", 1L));
    }

    @Test void remoteRejectionRetainsItsStatusAndAuditOutcome() {
        UUID id = UUID.randomUUID(), auditId = UUID.randomUUID();
        when(audit.start(any(), any(), any(), any(), any())).thenReturn(auditId);
        doThrow(new RemoteFailure(404, new RuntimeException("Missing"))).when(users).block(id, context.requestId());
        assertThatThrownBy(() -> service.block(context, id)).isInstanceOfSatisfying(AdminFailure.class, failure -> {
            assertThat(failure.status()).isEqualTo(404);
            assertThat(failure.code()).isEqualTo("REMOTE_REJECTED");
        });
        verify(audit).finish(auditId, "FAILED", Map.of("httpStatus", 404));
    }

    @Test void invalidPageAndBatchNeverCallAdapters() {
        assertThatThrownBy(() -> service.users(context, 0, 51, null)).isInstanceOf(AdminFailure.class);
        assertThatThrownBy(() -> service.sendMany(context, new SendMany(List.of(), UUID.randomUUID(), Map.of())))
                .isInstanceOf(AdminFailure.class);
        verifyNoInteractions(users, content, notifications, payments, audit);
    }
}
