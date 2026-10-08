package com.erudit.assessment.model;

import java.util.List;

public record AssessmentFeedbackContext(int erScore, List<TopicAssessmentScore> topicScores) {}
