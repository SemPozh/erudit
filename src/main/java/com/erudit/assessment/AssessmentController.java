package com.erudit.assessment;

import com.erudit.openapi.api.AssessmentApi;
import com.erudit.openapi.model.AssessmentResultResponse;
import com.erudit.openapi.model.AssessmentSessionResponse;
import com.erudit.openapi.model.AssessmentSubmitRequest;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssessmentController implements AssessmentApi {
    private final AssessmentService service;
    private final HttpServletRequest request;

    public AssessmentController(AssessmentService service, HttpServletRequest request) {
        this.service = service;
        this.request = request;
    }

    @Override
    public ResponseEntity<AssessmentSessionResponse> startAssessment() {
        AssessmentSession session = service.start(currentUser());
        var questions = session.questions().stream().map(question ->
                new com.erudit.openapi.model.AssessmentQuestion(question.id(), question.topic(), question.text(),
                        question.answers().stream().map(option ->
                                new com.erudit.openapi.model.AssessmentAnswerOption(option.id(), option.text()))
                                .toList())).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(new AssessmentSessionResponse(
                new com.erudit.openapi.model.AssessmentSession(session.id(), questions)));
    }

    @Override
    public ResponseEntity<AssessmentResultResponse> submitAssessment(AssessmentSubmitRequest body) {
        var answers = body.getAnswers().stream()
                .map(answer -> new SubmittedAnswer(answer.getQuestionId(), answer.getAnswerId())).toList();
        AssessmentSubmission result = service.submit(currentUser(), body.getAssessmentId(), answers);
        var response = new com.erudit.openapi.model.AssessmentResult(
                result.id(), result.correctAnswers(), result.totalAnswers());
        return ResponseEntity.ok(new AssessmentResultResponse(response));
    }

    @Override
    public ResponseEntity<AssessmentResultResponse> getAssessmentResult() {
        return ResponseEntity.notFound().build();
    }

    private String currentUser() {
        if (request.getUserPrincipal() == null) throw new UnauthorizedException("Authentication is required");
        return request.getUserPrincipal().getName();
    }
}
