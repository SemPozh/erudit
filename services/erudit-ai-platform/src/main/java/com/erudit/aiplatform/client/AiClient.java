package com.erudit.aiplatform.client;

import com.erudit.integration.InternalContracts.AiCard;
import com.erudit.integration.InternalContracts.AiQuiz;
import com.erudit.integration.InternalContracts.AiRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "erudit-ai-platform", contextId = "internalAiClient")
public interface AiClient {
    @PostMapping("/internal/v1/ai/quiz-generations")
    AiQuiz quiz(@RequestBody AiRequest request);

    @PostMapping("/internal/v1/ai/content-cards")
    AiCard card(@RequestBody AiRequest request);
}