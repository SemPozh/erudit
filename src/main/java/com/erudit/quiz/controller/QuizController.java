package com.erudit.quiz.controller;

import com.erudit.quiz.model.QuizAnswerOption;
import com.erudit.quiz.model.QuizAttempt;
import com.erudit.quiz.model.QuizQuestion;
import com.erudit.quiz.model.StartedQuiz;
import com.erudit.quiz.service.QuizAttemptService;

import com.erudit.assessment.model.SubmittedAnswer;
import com.erudit.openapi.api.QuizzesApi;
import com.erudit.openapi.model.*;
import com.erudit.web.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;


@RestController
public class QuizController implements QuizzesApi {

    private final QuizAttemptService service;
    private final HttpServletRequest request;

    public QuizController(QuizAttemptService service, HttpServletRequest request) {
        this.service = service;
        this.request = request;
    }

    @Override
    public ResponseEntity<QuizAttemptResponse> startQuiz(UUID id) {
        StartedQuiz started = service.start(currentUser(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAttemptResponse(started));
    }

    @Override
    public ResponseEntity<QuizResultResponse> submitQuiz(UUID id, QuizSubmitRequest body) {
        QuizAttempt done = service.submit(currentUser(), id, body.getAttemptId(), toSubmitted(body));
        return ResponseEntity.ok(toResultResponse(done));
    }

    @Override
    public ResponseEntity<QuizHintResponse> requestQuizHint(UUID id, UUID questionId, UUID attemptId) {
        String hint = service.requestHint(currentUser(), id, attemptId, questionId);
        return ResponseEntity.ok(new QuizHintResponse(
                new com.erudit.openapi.model.QuizHint(questionId, hint)));
    }

    @Override
    public ResponseEntity<QuizAttemptResponse> getDailyQuiz() {
        return ResponseEntity.ok(toAttemptResponse(service.startDaily(currentUser())));
    }

    @Override
    public ResponseEntity<QuizResultResponse> submitDailyQuiz(QuizSubmitRequest body) {
        QuizAttempt done = service.submitDaily(currentUser(), body.getAttemptId(), toSubmitted(body));
        return ResponseEntity.ok(toResultResponse(done));
    }

    private List<SubmittedAnswer> toSubmitted(QuizSubmitRequest body) {
        return body.getAnswers().stream()
                .map(a -> new SubmittedAnswer(a.getQuestionId(), a.getAnswerId()))
                .toList();
    }

    private QuizAttemptResponse toAttemptResponse(StartedQuiz started) {
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

        return new QuizAttemptResponse(
                new com.erudit.openapi.model.QuizAttempt(
                        started.attempt().id(), started.quiz().id(), questions));
    }

    private QuizResultResponse toResultResponse(QuizAttempt done) {
        return new QuizResultResponse(
                new com.erudit.openapi.model.QuizResult(
                        done.id(), done.correctAnswers(), done.score(), done.experience()));
    }

    private String currentUser() {
        if (request.getUserPrincipal() == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        return request.getUserPrincipal().getName();
    }
}