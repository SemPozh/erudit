package com.erudit.aiplatform.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerationService {
    private final GenerationProvider provider;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile Instant window = Instant.now();
    @Value("${ai.generation.timeout:2s}") private Duration timeout;
    @Value("${ai.generation.rate-limit:60}") private int rateLimit;

    public GenerationProvider.QuizResult quiz(String seed, String text) {
        return validate(await(() -> provider.quiz(seed, text)));
    }

    public GenerationProvider.CardResult card(String seed, String text) {
        return validate(await(() -> provider.card(seed, text)));
    }

    private <T> T await(java.util.function.Supplier<T> action) {
        acquire();
        try {
            return CompletableFuture.supplyAsync(action).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new GenerationException("AI_TIMEOUT", "Generation timed out");
        } catch (Exception exception) {
            throw new GenerationException("AI_PROVIDER_ERROR", "Generation provider failed");
        }
    }

    private synchronized void acquire() {
        if (Instant.now().isAfter(window.plusSeconds(60))) {
            window = Instant.now();
            requests.set(0);
        }
        if (requests.incrementAndGet() > rateLimit) {
            throw new GenerationException("AI_RATE_LIMIT", "Generation rate limit exceeded");
        }
    }

    private GenerationProvider.QuizResult validate(GenerationProvider.QuizResult result) {
        if (result == null || result.question() == null || result.question().isBlank()
                || result.options() == null || result.options().size() < 2
                || result.correctOption() < 0 || result.correctOption() >= result.options().size()) {
            throw new GenerationException("AI_MALFORMED_RESULT", "Provider returned an invalid quiz");
        }
        return result;
    }

    private GenerationProvider.CardResult validate(GenerationProvider.CardResult result) {
        if (result == null || result.title() == null || result.title().isBlank()
                || result.summary() == null || result.summary().isBlank()) {
            throw new GenerationException("AI_MALFORMED_RESULT", "Provider returned an invalid content card");
        }
        return result;
    }
}
