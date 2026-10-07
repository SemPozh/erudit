package com.erudit.assessment;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ErScoreCalculator {
    public static final int MAX_SCORE = 2000;

    public AssessmentScore calculate(List<AnswerOutcome> outcomes) {
        if (outcomes.isEmpty()) throw new IllegalArgumentException("At least one answer outcome is required");

        Map<String, TopicCounter> byTopic = new LinkedHashMap<>();
        int correct = 0;
        for (AnswerOutcome outcome : outcomes) {
            TopicCounter counter = byTopic.computeIfAbsent(outcome.topic(), ignored -> new TopicCounter());
            counter.total++;
            if (outcome.correct()) {
                counter.correct++;
                correct++;
            }
        }
        List<TopicAssessmentScore> topics = byTopic.entrySet().stream()
                .map(entry -> new TopicAssessmentScore(entry.getKey(), entry.getValue().correct,
                        entry.getValue().total, scale(entry.getValue().correct, entry.getValue().total)))
                .toList();
        return new AssessmentScore(scale(correct, outcomes.size()), topics);
    }

    private int scale(int correct, int total) {
        return (int) Math.round((double) correct * MAX_SCORE / total);
    }

    private static final class TopicCounter {
        private int correct;
        private int total;
    }
}
