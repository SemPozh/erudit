package com.erudit.aiplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erudit.aiplatform.service.GenerationException;
import com.erudit.aiplatform.service.GenerationProvider;
import com.erudit.aiplatform.service.GenerationService;
import com.erudit.aiplatform.service.MockGenerationProvider;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationServiceTest {
    @Test
    void mockIsDeterministicForSameSeed() throws Exception {
        GenerationService service = service(new MockGenerationProvider());
        var first = service.quiz("demo-seed", "The Earth revolves around the Sun.");
        var second = service.quiz("demo-seed", "The Earth revolves around the Sun.");
        assertThat(second).isEqualTo(first);
        assertThat(service.card("demo-seed", "The Earth revolves around the Sun."))
                .isEqualTo(service.card("demo-seed", "The Earth revolves around the Sun."));
    }

    @Test
    void enforcesRateLimitAndMapsProviderFailure() throws Exception {
        GenerationService limited = service(new MockGenerationProvider());
        set(limited, "rateLimit", 1);
        limited.quiz("seed", "sufficient source text");
        assertThatThrownBy(() -> limited.quiz("seed", "sufficient source text"))
                .isInstanceOf(GenerationException.class).extracting("code").isEqualTo("AI_RATE_LIMIT");

        GenerationProvider failing = new GenerationProvider() {
            public QuizResult quiz(String seed, String text) { throw new IllegalStateException("offline"); }
            public CardResult card(String seed, String text) { throw new IllegalStateException("offline"); }
        };
        assertThatThrownBy(() -> service(failing).quiz("seed", "sufficient source text"))
                .isInstanceOf(GenerationException.class).extracting("code").isEqualTo("AI_PROVIDER_ERROR");
    }

    @Test
    void timesOutSlowProvider() throws Exception {
        GenerationProvider slow = new GenerationProvider() {
            public QuizResult quiz(String seed, String text) {
                try { Thread.sleep(100); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                return new QuizResult("question", List.of("a", "b"), 0);
            }
            public CardResult card(String seed, String text) { return new CardResult("title", "summary"); }
        };
        GenerationService service = service(slow);
        set(service, "timeout", Duration.ofMillis(1));
        assertThatThrownBy(() -> service.quiz("seed", "sufficient source text"))
                .isInstanceOf(GenerationException.class).extracting("code").isEqualTo("AI_TIMEOUT");
    }

    @Test
    void rejectsMalformedProviderResult() throws Exception {
        GenerationProvider malformed = new GenerationProvider() {
            public QuizResult quiz(String seed, String text) { return new QuizResult("", List.of("one"), 4); }
            public CardResult card(String seed, String text) { return new CardResult("", ""); }
        };
        GenerationService service = service(malformed);
        assertThatThrownBy(() -> service.quiz("seed", "sufficient source text"))
                .isInstanceOf(GenerationException.class).hasMessageContaining("invalid quiz");
        assertThatThrownBy(() -> service.card("seed", "sufficient source text"))
                .isInstanceOf(GenerationException.class).hasMessageContaining("invalid content card");
    }

    private GenerationService service(GenerationProvider provider) throws Exception {
        GenerationService service = new GenerationService(provider);
        set(service, "timeout", Duration.ofSeconds(1));
        set(service, "rateLimit", 10);
        return service;
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
