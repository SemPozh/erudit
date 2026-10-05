package com.erudit.assessment;

import java.util.List;
import java.util.UUID;

public record AssessmentQuestion(UUID id, String topic, String text, List<AssessmentOption> answers) {}
