package com.erudit.assessment;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class StubAssessmentFeedbackProvider implements AssessmentFeedbackProvider {
    private static final Map<String, String> TOPIC_NAMES = Map.of(
            "SCIENCE", "наука",
            "HISTORY", "история",
            "TECHNOLOGY", "технологии",
            "CULTURE", "культура"
    );

    @Override
    public AssessmentFeedback generate(AssessmentFeedbackContext context) {
        if (context.topicScores().isEmpty()) {
            throw new IllegalArgumentException("Topic scores are required for feedback");
        }
        int strongestScore = context.topicScores().stream()
                .mapToInt(TopicAssessmentScore::erScore).max().orElseThrow();
        int weakestScore = context.topicScores().stream()
                .mapToInt(TopicAssessmentScore::erScore).min().orElseThrow();
        List<String> strongest = topicsWithScore(context.topicScores(), strongestScore);
        List<String> weakest = topicsWithScore(context.topicScores(), weakestScore);

        String text = levelSummary(context.erScore())
                + " Сильные темы: " + String.join(", ", strongest) + "."
                + " Основные направления развития: " + String.join(", ", weakest) + ".";
        List<String> recommendations = weakestScore == ErScoreCalculator.MAX_SCORE
                ? List.of("Закрепляйте результат заданиями повышенной сложности по всем темам.")
                : weakest.stream().map(topic -> "Изучите дополнительные материалы по теме «" + topic + "».").toList();
        return new AssessmentFeedback(text, recommendations);
    }

    private List<String> topicsWithScore(List<TopicAssessmentScore> topics, int score) {
        return topics.stream().filter(topic -> topic.erScore() == score)
                .map(topic -> TOPIC_NAMES.getOrDefault(topic.topic(), topic.topic()))
                .sorted(Comparator.naturalOrder()).toList();
    }

    private String levelSummary(int erScore) {
        if (erScore >= 1600) return "Вы показали высокий общий уровень эрудиции.";
        if (erScore >= 1000) return "У вас уверенная база знаний.";
        return "Рекомендуем укрепить базовые знания.";
    }
}
