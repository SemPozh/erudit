package com.erudit.assessment;

import java.util.UUID;

public record AssessmentOption(UUID id, UUID questionId, String text, boolean correct) {}
