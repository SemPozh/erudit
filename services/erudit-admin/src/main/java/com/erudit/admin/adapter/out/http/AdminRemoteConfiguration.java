package com.erudit.admin.adapter.out.http;

import com.erudit.admin.application.AdminModels.*;
import com.erudit.admin.application.port.AdminRemote;
import com.erudit.admin.application.port.RemoteFailure;
import feign.FeignException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

@Configuration(proxyBeanMethods = false)
public class AdminRemoteConfiguration {
    private final JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Bean AdminRemote.Users adminUsers(RemoteClients.Users client) {
        return new AdminRemote.Users() {
            public AdminRemote.UserPage search(String query, int page, int size, String requestId) {
                return call(() -> json.convertValue(client.search(query, page, size, requestId), AdminRemote.UserPage.class));
            }
            public void block(java.util.UUID id, String requestId) { call(() -> { client.block(id, requestId); return null; }); }
            public AdminRemote.UserState get(java.util.UUID id, String requestId) {
                return call(() -> json.convertValue(client.get(id, requestId), AdminRemote.UserState.class));
            }
        };
    }
    @Bean AdminRemote.Content adminContent(RemoteClients.Content client) {
        return new AdminRemote.Content() {
            public Page<ContentItem> queue(int page, int size, String requestId) {
                return call(() -> {
                    var response = client.queue(page, size, requestId);
                    var body = response.getBody();
                    return new Page<>(json.convertValue(body.getData(), new TypeReference<List<ContentItem>>() {}),
                            json.convertValue(body.getPagination(), Pagination.class), response.getHeaders().getFirst("X-Total-Count"));
                });
            }
            public AdminRemote.Moderated moderate(AdminRemote.Moderation request, java.util.UUID actor, String requestId) {
                return call(() -> json.convertValue(client.moderate(json.convertValue(request, RemoteClients.Moderation.class),
                        actor, requestId), AdminRemote.Moderated.class));
            }
            public Page<ContentReport> reports(int page, int size, String requestId) {
                return call(() -> {
                    var response = client.reports(page, size, requestId);
                    var body = response.getBody();
                    return new Page<>(json.convertValue(body.getData(), new TypeReference<List<ContentReport>>() {}),
                            json.convertValue(body.getPagination(), Pagination.class), response.getHeaders().getFirst("X-Total-Count"));
                });
            }
            public void resolve(java.util.UUID id, Resolution request, java.util.UUID actor, String requestId) {
                call(() -> { client.resolve(id, json.convertValue(request, com.erudit.admin.openapi.model.ReportResolutionRequest.class), actor, requestId); return null; });
            }
        };
    }
    @Bean AdminRemote.Notifications adminNotifications(RemoteClients.Notifications client) {
        return new AdminRemote.Notifications() {
            public AdminRemote.Template template(java.util.UUID id, String requestId) {
                return call(() -> json.convertValue(client.template(id, requestId), AdminRemote.Template.class));
            }
            public AdminRemote.NotificationAck enqueue(AdminRemote.Enqueue request, String requestId) {
                return call(() -> json.convertValue(client.enqueue(json.convertValue(request, RemoteClients.Enqueue.class), requestId), AdminRemote.NotificationAck.class));
            }
        };
    }
    @Bean AdminRemote.Payments adminPayments(RemoteClients.Payments client) {
        return new AdminRemote.Payments() {
            public Subscription subscription(java.util.UUID user, String requestId) {
                return call(() -> json.convertValue(client.subscription(user, requestId).getData(), Subscription.class));
            }
            public Page<Payment> payments(int page, int size, java.util.UUID user, String requestId) {
                return call(() -> {
                    var response = client.payments(page, size, user, requestId);
                    var body = response.getBody();
                    return new Page<>(json.convertValue(body.getData(), new TypeReference<List<Payment>>() {}),
                            json.convertValue(body.getPagination(), Pagination.class), response.getHeaders().getFirst("X-Total-Count"));
                });
            }
        };
    }
    private static <T> T call(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof FeignException remote) throw new RemoteFailure(remote.status(), failure);
            }
            throw failure;
        }
    }
}
