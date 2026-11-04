package com.erudit.quiz.controller;
import com.erudit.quiz.model.Assessment.Answer;
import com.erudit.quiz.openapi.api.AssessmentApi;
import com.erudit.quiz.openapi.api.LearningProgramApi;
import com.erudit.quiz.openapi.model.*;
import com.erudit.quiz.service.AssessmentService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequiredArgsConstructor
public class AssessmentController implements AssessmentApi,LearningProgramApi {
 private final AssessmentService service;
 @Override public ResponseEntity<List<QuestionResponse>> listAssessmentQuestions(){return ResponseEntity.ok(service.questions().stream().map(q->new QuestionResponse(q.id(),q.prompt(),q.options())).toList());}
 @Override public ResponseEntity<SessionResponse> startAssessment(UUID user){var v=service.start(user);return ResponseEntity.status(201).body(new SessionResponse(v.id(),v.userId(),v.status()));}
 @Override public ResponseEntity<ResultResponse> submitAssessment(UUID id,SubmitRequest request){var v=service.submit(id,request.getAnswers().stream().map(a->new Answer(a.getQuestionId(),a.getOptionIndex())).toList());return ResponseEntity.ok(result(v));}
 @Override public ResponseEntity<ResultResponse> getAssessmentResult(UUID id){return ResponseEntity.ok(result(service.get(id)));}
 @Override public ResponseEntity<ProgramResponse> getLearningProgram(UUID user){var v=service.program(user);return ResponseEntity.ok(new ProgramResponse(v.userId(),v.grade(),v.progress()));}
 @Override public ResponseEntity<StepResponse> getNextLearningStep(UUID user){var v=service.next(user);return ResponseEntity.ok(new StepResponse(v.code(),v.title()));}
 private static ResultResponse result(com.erudit.quiz.model.Assessment.Session v){if(v.score()==null)throw new IllegalArgumentException("Assessment is not completed");return new ResultResponse(v.id(),v.score(),v.grade());}
}
