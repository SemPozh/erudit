package com.erudit.assessment.model;

import java.util.List;

public record AssessmentFeedback(String text, List<String> recommendations) {
    public AssessmentFeedback {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Feedback text is required");
        recommendations = List.copyOf(recommendations);
    }
}
