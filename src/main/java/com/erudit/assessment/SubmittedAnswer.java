package com.erudit.assessment;

import java.util.UUID;

public record SubmittedAnswer(UUID questionId, UUID answerId) {}
