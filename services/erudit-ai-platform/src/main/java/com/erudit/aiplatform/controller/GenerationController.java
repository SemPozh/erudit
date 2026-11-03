package com.erudit.aiplatform.controller;

import com.erudit.aiplatform.openapi.api.AiGenerationApi;
import com.erudit.aiplatform.openapi.model.ContentCardResponse;
import com.erudit.aiplatform.openapi.model.GenerationRequest;
import com.erudit.aiplatform.openapi.model.QuizGenerationResponse;
import com.erudit.aiplatform.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GenerationController implements AiGenerationApi {
    private final GenerationService service;

    @Override
    public ResponseEntity<ContentCardResponse> generateContentCard(GenerationRequest request) {
        var result = service.card(request.getSeed(), request.getSourceText());
        return ResponseEntity.ok(new ContentCardResponse(result.title(), result.summary()));
    }

    @Override
    public ResponseEntity<QuizGenerationResponse> generateQuiz(GenerationRequest request) {
        var result = service.quiz(request.getSeed(), request.getSourceText());
        return ResponseEntity.ok(new QuizGenerationResponse(result.question(), result.options(), result.correctOption()));
    }
}
