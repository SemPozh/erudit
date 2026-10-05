package com.erudit.assessment;

import java.util.List;

public record AssessmentFeedbackContext(int erScore, List<TopicAssessmentScore> topicScores) {}
