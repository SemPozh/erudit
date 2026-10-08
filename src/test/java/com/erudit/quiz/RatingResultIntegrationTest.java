package com.erudit.quiz;

import com.erudit.quiz.model.Quiz;
import com.erudit.quiz.model.QuizAnswerOption;
import com.erudit.quiz.model.QuizAttempt;
import com.erudit.quiz.model.QuizQuestion;
import com.erudit.quiz.repository.QuizRepository;
import com.erudit.quiz.service.QuizAttemptService;

import com.erudit.assessment.model.SubmittedAnswer;
import com.erudit.events.service.AnalyticsEventPublisher;
import com.erudit.events.model.EventType;
import com.erudit.rating.repository.RatingRepository;
import com.erudit.rating.service.RatingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class RatingResultIntegrationTest {
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private RatingService ratingService;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean private AnalyticsEventPublisher publisher;

    @Test
    void quizAndCompetitionResultsUpdatePointsErScoreGradeAndEventsIdempotently() {
        QuizTestFixture.StoredContent content = QuizTestFixture.contentWithCard(jdbc);
        Quiz quiz = QuizTestFixture.quiz(content);
        quizRepository.save(quiz);
        String userId = "rating-" + UUID.randomUUID();

        QuizAttempt attempt = attemptService.start(userId, quiz.id()).attempt();
        QuizQuestion question = quiz.questions().getFirst();
        QuizAttempt finished = attemptService.submit(userId, quiz.id(), attempt.id(), List.of(
                new SubmittedAnswer(question.id(), question.answers().stream()
                        .filter(QuizAnswerOption::correct).findFirst().orElseThrow().id())));

        assertThat(finished.score()).isOne();
        var afterQuiz = ratingRepository.findProfile(userId).orElseThrow();
        assertThat(afterQuiz.points()).isEqualTo(2);
        assertThat(afterQuiz.erScore()).isEqualTo(1);
        assertThat(afterQuiz.gradeCode()).isEqualTo("NOVICE_I");

        UUID competitionId = UUID.randomUUID();
        ratingService.recordCompetitionResult(userId, competitionId, quiz.id(), 5, 1, 3);
        ratingService.recordCompetitionResult(userId, competitionId, quiz.id(), 5, 1, 3);

        var afterCompetition = ratingRepository.findProfile(userId).orElseThrow();
        assertThat(afterCompetition.points()).isEqualTo(10);
        assertThat(afterCompetition.erScore()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rating_events WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(2);
        verify(publisher, atLeastOnce()).publish(argThat(event ->
                event.eventType() == EventType.QUIZ_COMPLETED ||
                        event.eventType() == EventType.COMPETITION_COMPLETED));
    }
}
