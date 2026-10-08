package com.erudit.assessment;

import com.erudit.assessment.model.AnswerOutcome;
import com.erudit.assessment.model.AssessmentScore;
import com.erudit.assessment.model.TopicAssessmentScore;
import com.erudit.assessment.service.ErScoreCalculator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErScoreCalculatorTest {
    private final ErScoreCalculator calculator = new ErScoreCalculator();

    @Test
    void calculatesOverallAndTopicScoresOnTheSameScale() {
        AssessmentScore result = calculator.calculate(List.of(
                new AnswerOutcome("SCIENCE", true),
                new AnswerOutcome("SCIENCE", false),
                new AnswerOutcome("HISTORY", true)));

        assertThat(result.erScore()).isEqualTo(1333);
        assertThat(result.topics()).containsExactly(
                new TopicAssessmentScore("SCIENCE", 1, 2, 1000),
                new TopicAssessmentScore("HISTORY", 1, 1, 2000));
    }

    @Test
    void keepsBoundaryScoresWithinRangeAndRejectsEmptyInput() {
        assertThat(calculator.calculate(List.of(new AnswerOutcome("SCIENCE", false))).erScore()).isZero();
        assertThat(calculator.calculate(List.of(new AnswerOutcome("SCIENCE", true))).erScore()).isEqualTo(2000);
        assertThatThrownBy(() -> calculator.calculate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
