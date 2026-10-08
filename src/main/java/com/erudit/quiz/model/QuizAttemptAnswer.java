package com.erudit.quiz.model;

import java.util.UUID;

public record QuizAttemptAnswer(UUID attemptId, UUID questionId, UUID answerId, boolean correct) {}