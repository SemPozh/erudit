package com.erudit.aiplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.erudit.aiplatform.openapi.model.GenerationRequest;
import com.erudit.aiplatform.service.GenerationException;
import com.erudit.aiplatform.service.GenerationProvider;
import com.erudit.aiplatform.service.GenerationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GenerationControllerTest {
    private final GenerationService service = org.mockito.Mockito.mock(GenerationService.class);
    private final GenerationController controller = new GenerationController(service);

    @Test
    void mapsQuizAndCardResponses() {
        GenerationRequest request = new GenerationRequest("seed", "sufficient source text");
        when(service.quiz("seed", "sufficient source text"))
                .thenReturn(new GenerationProvider.QuizResult("question", List.of("a", "b"), 1));
        when(service.card("seed", "sufficient source text"))
                .thenReturn(new GenerationProvider.CardResult("title", "summary"));
        assertThat(controller.generateQuiz(request).getBody().getCorrectOption()).isEqualTo(1);
        assertThat(controller.generateContentCard(request).getBody().getTitle()).isEqualTo("title");
    }

    @Test
    void mapsStructuredErrorStatuses() {
        GenerationExceptionHandler handler = new GenerationExceptionHandler();
        assertThat(handler.generation(new GenerationException("AI_RATE_LIMIT", "limited")).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(handler.generation(new GenerationException("AI_TIMEOUT", "slow")).getStatusCode())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(handler.generation(new GenerationException("AI_MALFORMED_RESULT", "bad")).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
