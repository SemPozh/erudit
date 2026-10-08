package com.erudit.assessment.model;

import java.util.List;

public record AssessmentScore(int erScore, List<TopicAssessmentScore> topics) {}
