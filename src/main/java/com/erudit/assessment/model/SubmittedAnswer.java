package com.erudit.assessment.model;

import java.util.UUID;

public record SubmittedAnswer(UUID questionId, UUID answerId) {}
