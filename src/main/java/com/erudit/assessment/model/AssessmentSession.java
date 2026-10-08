package com.erudit.assessment.model;

import java.util.List;
import java.util.UUID;

public record AssessmentSession(UUID id, String userId, List<AssessmentQuestion> questions) {}
