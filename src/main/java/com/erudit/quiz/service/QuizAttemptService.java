package com.erudit.quiz.service;

import com.erudit.quiz.model.Quiz;
import com.erudit.quiz.model.QuizAnswerOption;
import com.erudit.quiz.model.QuizAttempt;
import com.erudit.quiz.model.QuizAttemptAnswer;
import com.erudit.quiz.model.QuizQuestion;
import com.erudit.quiz.model.StartedQuiz;
import com.erudit.quiz.repository.DailyQuizRepository;
import com.erudit.quiz.repository.QuizAttemptRepository;
import com.erudit.quiz.repository.QuizHintRepository;
import com.erudit.quiz.repository.QuizRepository;

import com.erudit.assessment.model.SubmittedAnswer;
import com.erudit.openapi.model.AssessmentSubmitRequestAnswersInner;
import com.erudit.openapi.model.QuizResult;
import com.erudit.openapi.model.QuizSubmitRequest;
import com.erudit.rating.service.RatingService;
import com.erudit.web.exception.NotFoundException;
import com.erudit.web.exception.ValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
public class QuizAttemptService {

    // REQ 5.4 gives no formula, so this is our own choice:
    // score = correct answers minus hints used (never below 0),
    // experience = score + 1 for finishing.
    static final int COMPLETION_BONUS = 1;

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizHintRepository hintRepository;
    private final DailyQuizRepository dailyRepository;
    private final Clock clock;
    private final RatingService ratingService;

    public QuizAttemptService(QuizRepository quizRepository,
                              QuizAttemptRepository attemptRepository,
                              QuizHintRepository hintRepository,
                              DailyQuizRepository dailyRepository,
                              Clock clock,
                              RatingService ratingService) {
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.hintRepository = hintRepository;
        this.dailyRepository = dailyRepository;
        this.clock = clock;
        this.ratingService = ratingService;
    }

    // ---------- regular quiz ----------

    public StartedQuiz start(String username, UUID quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new NotFoundException("Quiz not found"));

        var attempt = new QuizAttempt(UUID.randomUUID(), quizId, username,
                clock.instant(), null, null, null, null);
        attemptRepository.create(attempt);

        return new StartedQuiz(attempt, quiz);
    }

    @Transactional
    public QuizAttempt submit(String username, UUID quizId, UUID attemptId,
                              List<SubmittedAnswer> submitted) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new NotFoundException("Quiz not found"));
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt not found"));

        if (!attempt.userId().equals(username)) {
            throw new NotFoundException("Attempt not found");
        }
        if (!attempt.quizId().equals(quizId)) {
            throw new ValidationException("Attempt does not belong to this quiz");
        }
        return grade(quiz, attempt, submitted);
    }

    // ---------- daily quiz ----------

    public StartedQuiz startDaily(String username) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

        Optional<QuizAttempt> existing = attemptRepository.findDaily(username, today);
        if (existing.isPresent()) {
            QuizAttempt attempt = existing.get();
            if (attempt.submitted()) {
                throw new ValidationException("Daily quiz already completed today");
            }
            Quiz quiz = quizRepository.findById(attempt.quizId())
                    .orElseThrow(() -> new NotFoundException("Quiz not found"));
            return new StartedQuiz(attempt, quiz);
        }

        Quiz quiz = pickDailyQuiz(username, today);
        var attempt = new QuizAttempt(UUID.randomUUID(), quiz.id(), username,
                clock.instant(), null, null, null, null);
        try {
            attemptRepository.createDaily(attempt, today);
        } catch (DuplicateKeyException e) {
            // a parallel request created today's attempt first: return that one
            return startDaily(username);
        }
        return new StartedQuiz(attempt, quiz);
    }

    @Transactional
    public QuizAttempt submitDaily(String username, UUID attemptId,
                                   List<SubmittedAnswer> submitted) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt not found"));

        if (!attempt.userId().equals(username)) {
            throw new NotFoundException("Attempt not found");
        }
        if (!attemptRepository.isDaily(attemptId)) {
            throw new ValidationException("Attempt is not a daily quiz attempt");
        }
        Quiz quiz = quizRepository.findById(attempt.quizId())
                .orElseThrow(() -> new NotFoundException("Quiz not found"));
        return grade(quiz, attempt, submitted);
    }

    /**
     * Quizzes for content the user has completed; if there are none, any quiz.
     * The pick is a stable hash of user + date, so the same user gets the same
     * quiz all day and a different one on other days.
     */
    private Quiz pickDailyQuiz(String username, LocalDate today) {
        List<UUID> ids = dailyRepository.quizIdsForCompletedContent(username);
        if (ids.isEmpty()) {
            ids = dailyRepository.allQuizIds();
        }
        if (ids.isEmpty()) {
            throw new NotFoundException("No quizzes available");
        }
        UUID id = ids.get(Math.floorMod((username + today).hashCode(), ids.size()));
        return quizRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Quiz not found"));
    }

    // ---------- hints ----------

    public String requestHint(String username, UUID quizId, UUID attemptId, UUID questionId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new NotFoundException("Quiz not found"));
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt not found"));

        if (!attempt.userId().equals(username)) {
            throw new NotFoundException("Attempt not found");
        }
        if (!attempt.quizId().equals(quizId)) {
            throw new ValidationException("Attempt does not belong to this quiz");
        }
        if (attempt.submitted()) {
            throw new ValidationException("Attempt already submitted");
        }

        QuizQuestion question = quiz.questions().stream()
                .filter(q -> q.id().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Question does not belong to this quiz"));

        String hint = Optional.ofNullable(question.quizCardId())
                .flatMap(hintRepository::findFact)
                .orElseThrow(() -> new NotFoundException("No hint available for this question"));

        hintRepository.record(attemptId, questionId, clock.instant());
        return hint;
    }

    // ---------- shared grading ----------

    private QuizAttempt grade(Quiz quiz, QuizAttempt attempt, List<SubmittedAnswer> submitted) {
        if (attempt.submitted()) {
            throw new ValidationException("Attempt already submitted");
        }

        Map<UUID, QuizQuestion> questions = quiz.questions().stream()
                .collect(Collectors.toMap(QuizQuestion::id, q -> q));

        Map<UUID, UUID> chosen = new HashMap<>();
        for (SubmittedAnswer a : submitted) {
            if (chosen.put(a.questionId(), a.answerId()) != null) {
                throw new ValidationException("Duplicate answer for question " + a.questionId());
            }
        }
        if (!chosen.keySet().equals(questions.keySet())) {
            throw new ValidationException("Answers must cover exactly the questions of the quiz");
        }

        List<QuizAttemptAnswer> graded = new ArrayList<>();
        int correct = 0;
        for (var entry : chosen.entrySet()) {
            QuizAnswerOption option = questions.get(entry.getKey()).answers().stream()
                    .filter(o -> o.id().equals(entry.getValue()))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException(
                            "Answer does not belong to question " + entry.getKey()));
            if (option.correct()) {
                correct++;
            }
            graded.add(new QuizAttemptAnswer(attempt.id(), entry.getKey(), option.id(), option.correct()));
        }

        int hintsUsed = hintRepository.countForAttempt(attempt.id());
        int score = Math.max(0, correct - hintsUsed);

        var finished = new QuizAttempt(attempt.id(), attempt.quizId(), attempt.userId(),
                attempt.startedAt(), clock.instant(),
                correct, score, score + COMPLETION_BONUS);

        if (!attemptRepository.submit(finished, graded)) {
            throw new ValidationException("Attempt already submitted");
        }
        ratingService.recordQuizResult(quiz, finished);
        return finished;
    }
}
