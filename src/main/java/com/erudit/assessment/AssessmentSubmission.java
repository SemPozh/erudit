package com.erudit.assessment;

import java.util.UUID;

public record AssessmentSubmission(UUID id, int correctAnswers, int totalAnswers) {}
