package com.erudit.quiz.adapter.out.http;

import com.erudit.quiz.application.port.QuizGeneration;
import com.erudit.quiz.application.port.CompetitionParticipants;
import com.erudit.aiplatform.client.AiClient;
import com.erudit.user.client.UserClient;
import com.erudit.notificator.client.NotificationClient;
import com.erudit.integration.InternalContracts.AiRequest;
import com.erudit.integration.InternalContracts.Notification;
import com.erudit.integration.ResilientExecutor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QuizRemoteAdapter implements QuizGeneration, CompetitionParticipants {
    private final AiClient ai;
    private final UserClient users;
    private final NotificationClient notifications;
    private final CircuitBreakerRegistry breakers;

    public Generated generate(String title, String prompt) {
        var result = ResilientExecutor.execute(breakers.circuitBreaker("ai"),
                () -> ai.quiz(new AiRequest(title, prompt)), () -> null);
        return result == null ? null : new Generated(result.question(), result.options(), result.correctOption());
    }
    public boolean active(UUID user) {
        return ResilientExecutor.execute(breakers.circuitBreaker("internal"), () -> users.get(user).active(), () -> false);
    }
    public void invited(UUID competition, UUID user) {
        ResilientExecutor.execute(breakers.circuitBreaker("internal"), () -> {
            notifications.enqueue(new Notification(user, "COMPETITION_INVITE", Map.of("competitionId", competition.toString())));
            return true;
        }, () -> false);
    }
}
