package com.erudit.rating;

import com.erudit.assessment.ErScoreCalculator;
import com.erudit.assessment.GradeService;
import com.erudit.events.AnalyticsEventPublisher;
import com.erudit.events.EventPublication;
import com.erudit.events.EventType;
import com.erudit.quiz.Quiz;
import com.erudit.quiz.QuizAttempt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class RatingService {
    private final RatingRepository repository;
    private final GradeService gradeService;
    private final ObjectProvider<AnalyticsEventPublisher> publisherProvider;
    private final Clock clock;

    public RatingService(RatingRepository repository, GradeService gradeService,
                         ObjectProvider<AnalyticsEventPublisher> publisherProvider, Clock clock) {
        this.repository = repository;
        this.gradeService = gradeService;
        this.publisherProvider = publisherProvider;
        this.clock = clock;
    }

    @Transactional
    public RatingUpdate recordQuizResult(Quiz quiz, QuizAttempt attempt) {
        // Completion experience drives rating points; only demonstrated knowledge changes ER-score.
        int points = attempt.experience();
        int erDelta = attempt.score();
        RatingUpdate update = award(attempt.userId(), "QUIZ", attempt.id(), quiz.id(), points, erDelta);
        if (update.awarded()) {
            publishAfterCommit(EventType.QUIZ_COMPLETED, attempt.userId(), attempt.id(), points, update.erScore());
        }
        return update;
    }

    @Transactional
    public RatingUpdate recordCompetitionResult(String userId, UUID competitionId, UUID quizId,
                                                int score, int rank, int participantCount) {
        // Better placement increases both rating points and ER-score; a finisher gets at least one point.
        int placementBonus = Math.max(1, participantCount - rank + 1);
        int points = Math.max(0, score) + placementBonus;
        RatingUpdate update = award(userId, "COMPETITION", competitionId, quizId, points, placementBonus);
        if (update.awarded()) {
            publishAfterCommit(EventType.COMPETITION_COMPLETED, userId, competitionId, points, update.erScore());
        }
        return update;
    }

    private RatingUpdate award(String userId, String sourceType, UUID sourceId, UUID quizId,
                               int points, int erDelta) {
        RatingProfile current = repository.findProfile(userId).orElseGet(() -> {
            int baseline = repository.assessmentErScore(userId);
            return new RatingProfile(userId, 0, baseline, gradeService.resolve(baseline).code());
        });
        if (repository.eventExists(userId, sourceType, sourceId)) {
            return new RatingUpdate(false, current.points(), current.erScore(), current.gradeCode());
        }
        int erScore = Math.min(ErScoreCalculator.MAX_SCORE, current.erScore() + Math.max(0, erDelta));
        RatingProfile updated = new RatingProfile(userId, current.points() + Math.max(0, points),
                erScore, gradeService.resolve(erScore).code());
        Instant now = clock.instant();
        repository.insertEvent(userId, sourceType, sourceId, repository.categoryForQuiz(quizId),
                Math.max(0, points), Math.max(0, erDelta), now);
        repository.saveProfile(updated, now);
        return new RatingUpdate(true, updated.points(), updated.erScore(), updated.gradeCode());
    }

    private void publishAfterCommit(EventType type, String userId, UUID sourceId, int points, int erScore) {
        Runnable publish = () -> {
            AnalyticsEventPublisher publisher = publisherProvider.getIfAvailable();
            if (publisher != null) {
                publisher.publish(new EventPublication(type, userId, "rating:" + sourceId,
                        "{\"sourceId\":\"" + sourceId + "\",\"points\":" + points
                                + ",\"erScore\":" + erScore + "}"));
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }
}
