package com.erudit.quiz;

import com.erudit.assessment.SubmittedAnswer;
import com.erudit.openapi.api.QuizzesApi;
import com.erudit.openapi.model.QuizAttemptResponse;
import com.erudit.openapi.model.QuizCardListResponse;
import com.erudit.openapi.model.QuizResultResponse;
import com.erudit.openapi.model.QuizSubmitRequest;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.UUID;

@RestController
public class QuizController implements QuizzesApi {   // use the generated interface name

    private final QuizAttemptService service;
    private final HttpServletRequest request;

    public QuizController(QuizAttemptService service, HttpServletRequest request) {
        this.service = service;
        this.request = request;
    }

    @Override
    public ResponseEntity<QuizAttemptResponse> startQuiz(UUID id) {
        StartedQuiz started = service.start(currentUser(), id);

        var questions = started.quiz().questions().stream()
                .sorted(Comparator.comparingInt(QuizQuestion::position))
                .map(q -> new com.erudit.openapi.model.QuizAttemptQuestion(
                        q.id(),
                        q.text(),
                        q.answers().stream()
                                .sorted(Comparator.comparingInt(QuizAnswerOption::position))
                                .map(a -> new com.erudit.openapi.model.QuizAttemptAnswerOption(
                                        a.id(), a.text()))
                                .toList()))
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new QuizAttemptResponse(
                        new com.erudit.openapi.model.QuizAttempt(
                                started.attempt().id(), started.quiz().id(), questions)));
    }

    @Override
    public ResponseEntity<QuizResultResponse> submitDailyQuiz(QuizSubmitRequest quizSubmitRequest) {
        return null;
    }

    @Override
    public ResponseEntity<QuizResultResponse> submitQuiz(UUID id, QuizSubmitRequest body) {
        var answers = body.getAnswers().stream()
                .map(a -> new SubmittedAnswer(a.getQuestionId(), a.getAnswerId()))
                .toList();

        QuizAttempt done = service.submit(currentUser(), id, body.getAttemptId(), answers);

        return ResponseEntity.ok(new QuizResultResponse(
                new com.erudit.openapi.model.QuizResult(
                        done.id(), done.correctAnswers(), done.score(), done.experience())));
    }

    private String currentUser() {
        if (request.getUserPrincipal() == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        return request.getUserPrincipal().getName();
    }

    @Override
    public ResponseEntity<QuizAttemptResponse> getDailyQuiz() {
        return null;
    }

    @Override
    public ResponseEntity<QuizCardListResponse> requestQuizHint(UUID id, UUID questionId) {
        return null;
    }
}