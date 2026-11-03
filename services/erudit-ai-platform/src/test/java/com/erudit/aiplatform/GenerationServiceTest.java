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
