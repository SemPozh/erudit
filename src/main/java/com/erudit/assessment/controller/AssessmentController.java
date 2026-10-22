package com.erudit.assessment.controller;

import lombok.RequiredArgsConstructor;
import com.erudit.assessment.dto.AssessmentSubmissionResponse;
import com.erudit.assessment.model.AssessmentQuestion;
import com.erudit.assessment.model.AssessmentSession;
import com.erudit.assessment.model.SubmittedAnswer;
import com.erudit.assessment.service.AssessmentService;

import com.erudit.openapi.api.AssessmentApi;
import com.erudit.openapi.model.AssessmentResultResponse;
import com.erudit.openapi.model.AssessmentSessionResponse;
import com.erudit.openapi.model.AssessmentSubmitRequest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AssessmentController implements AssessmentApi {
    private final AssessmentService service;
    private final HttpServletRequest request;



    @Override
    public ResponseEntity<AssessmentSessionResponse> startAssessment() {
        AssessmentSession session = service.start(currentUser());
        var questions = session.questions().stream().map(question ->
                new com.erudit.openapi.model.AssessmentQuestionDataResponse(question.id(), question.topic(), question.text(),
                        question.answers().stream().map(option ->
                                new com.erudit.openapi.model.AssessmentAnswerOptionDataResponse(option.id(), option.text()))
                                .toList())).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(new AssessmentSessionResponse(
                new com.erudit.openapi.model.AssessmentSessionDataResponse(session.id(), questions)));
    }

    @Override
    public ResponseEntity<AssessmentResultResponse> submitAssessment(AssessmentSubmitRequest body) {
        var answers = body.getAnswers().stream()
                .map(answer -> new SubmittedAnswer(answer.getQuestionId(), answer.getAnswerId())).toList();
        AssessmentSubmissionResponse result = service.submit(currentUser(), body.getAssessmentId(), answers);
        return ResponseEntity.ok(response(result));
    }

    @Override
    public ResponseEntity<AssessmentResultResponse> getAssessmentResult() {
        return ResponseEntity.ok(response(service.latestResult(currentUser())));
    }

    private AssessmentResultResponse response(AssessmentSubmissionResponse result) {
        var topics = result.topicScores().stream().map(topic ->
                new com.erudit.openapi.model.AssessmentTopicScoreDataResponse(topic.topic(), topic.correctAnswers(),
                        topic.totalAnswers(), (double) topic.erScore())).toList();
        var response = new com.erudit.openapi.model.AssessmentResultDataResponse(
                result.id(), (double) result.erScore(), topics, result.grade().title(),
                result.feedback().text(), result.feedback().recommendations(),
                result.correctAnswers(), result.totalAnswers());
        return new AssessmentResultResponse(response);
    }

    private String currentUser() {
        return com.erudit.user.config.LabOneRequestIdentity.name(request);
    }
}
