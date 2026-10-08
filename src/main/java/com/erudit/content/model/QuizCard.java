package com.erudit.content.model;

import java.util.List;
import java.util.UUID;

public record QuizCard(UUID id, UUID contentId, int position, String fact,
                       String question, String correctAnswer, List<String> answers) {
}
