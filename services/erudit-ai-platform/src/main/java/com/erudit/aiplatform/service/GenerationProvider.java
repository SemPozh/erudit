package com.erudit.aiplatform.service;

import java.util.List;

public interface GenerationProvider {
    QuizResult quiz(String seed, String sourceText);
    CardResult card(String seed, String sourceText);

    record QuizResult(String question, List<String> options, int correctOption) {}
    record CardResult(String title, String summary) {}
}
