package com.erudit.assessment.service;

import com.erudit.assessment.model.AssessmentFeedback;
import com.erudit.assessment.model.AssessmentFeedbackContext;

public interface AssessmentFeedbackProvider {
    AssessmentFeedback generate(AssessmentFeedbackContext context);
}
