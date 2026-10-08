package com.erudit.assessment.dto;

import com.erudit.assessment.model.AssessmentFeedback;
import com.erudit.assessment.model.Grade;
import com.erudit.assessment.model.TopicAssessmentScore;

import java.util.List;
import java.util.UUID;

public record AssessmentSubmission(UUID id, int correctAnswers, int totalAnswers, int erScore,
                                   List<TopicAssessmentScore> topicScores, Grade grade,
                                   AssessmentFeedback feedback) {}
