package com.erudit.assessment;

import java.util.List;

public record AssessmentScore(int erScore, List<TopicAssessmentScore> topics) {}
