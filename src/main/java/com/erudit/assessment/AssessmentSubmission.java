package com.erudit.assessment;

import java.util.List;
import java.util.UUID;

public record AssessmentSubmission(UUID id, int correctAnswers, int totalAnswers, int erScore,
                                   List<TopicAssessmentScore> topicScores, Grade grade,
                                   AssessmentFeedback feedback) {}
