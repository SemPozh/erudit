package com.erudit.quiz;

import com.erudit.assessment.SubmittedAnswer;
import com.erudit.openapi.model.AssessmentSubmitRequestAnswersInner;
import com.erudit.openapi.model.QuizResult;
import com.erudit.openapi.model.QuizSubmitRequest;
import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuizAttemptService {

    // REQ 5.4 gives no formula, so this is our own choice:
    // score = number of correct answers, experience = correct answers + 1 for finishing.
    static final int COMPLETION_BONUS = 1;

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final Clock clock;
    private final QuizHintRepository hintRepository;

    // TODO: inject whatever AssessmentService uses to turn the username into a user id
    // private final UserRepository userRepository;

    public QuizAttemptService(QuizRepository quizRepository,
                              QuizAttemptRepository attemptRepository,
                              Clock clock, QuizHintRepository hintRepository) {
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.clock = clock;
        this.hintRepository = hintRepository;
    }

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
            graded.add(new QuizAttemptAnswer(attemptId, entry.getKey(), option.id(), option.correct()));
        }

        // Each hint used costs one point (REQ 5.5 gives no formula, this is our choice).
        int hintsUsed = hintRepository.countForAttempt(attemptId);
        int score = Math.max(0, correct - hintsUsed);

        var finished = new QuizAttempt(attempt.id(), quizId, attempt.userId(),
                attempt.startedAt(), clock.instant(),
                correct, score, score + COMPLETION_BONUS);

        if (!attemptRepository.submit(finished, graded)) {
            throw new ValidationException("Attempt already submitted");
        }
        return finished;
    }

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
}