package com.erudit.assessment;

import com.erudit.assessment.model.AssessmentFeedback;
import com.erudit.assessment.model.AssessmentFeedbackContext;
import com.erudit.assessment.model.TopicAssessmentScore;
import com.erudit.assessment.service.AssessmentFeedbackProvider;
import com.erudit.assessment.service.StubAssessmentFeedbackProvider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubAssessmentFeedbackProviderTest {
    private final AssessmentFeedbackProvider provider = new StubAssessmentFeedbackProvider();

    @Test
    void describesStrongAndWeakTopicsAndRecommendsWeakestOnes() {
        AssessmentFeedback feedback = provider.generate(new AssessmentFeedbackContext(1500, List.of(
                new TopicAssessmentScore("SCIENCE", 1, 1, 2000),
                new TopicAssessmentScore("HISTORY", 1, 1, 2000),
                new TopicAssessmentScore("CULTURE", 0, 1, 0))));

        assertThat(feedback.text()).contains("уверенная база", "история, наука", "культура");
        assertThat(feedback.recommendations())
                .containsExactly("Изучите дополнительные материалы по теме «культура».");
    }

    @Test
    void recommendsAdvancedPracticeForPerfectResult() {
        AssessmentFeedback feedback = provider.generate(new AssessmentFeedbackContext(2000, List.of(
                new TopicAssessmentScore("SCIENCE", 1, 1, 2000),
                new TopicAssessmentScore("HISTORY", 1, 1, 2000))));

        assertThat(feedback.text()).contains("высокий общий уровень");
        assertThat(feedback.recommendations()).singleElement().asString().contains("повышенной сложности");
    }

    @Test
    void rejectsContextWithoutTopicScores() {
        assertThatThrownBy(() -> provider.generate(new AssessmentFeedbackContext(0, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
