package com.erudit.quiz.controller;import com.erudit.quiz.model.QuizModels.*;import com.erudit.quiz.openapi.api.*;import com.erudit.quiz.openapi.model.*;import com.erudit.quiz.service.QuizService;import java.util.*;import lombok.RequiredArgsConstructor;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.RestController;
@RestController @RequiredArgsConstructor public class QuizController implements QuizzesApi,QuizAttemptsApi,QuizAuthoringApi {private final QuizService service;
 @Override public ResponseEntity<List<QuizResponse>> listQuizzes(){return ResponseEntity.ok(service.list().stream().map(QuizController::response).toList());}
 @Override public ResponseEntity<QuizResponse> dailyQuiz(){return ResponseEntity.ok(response(service.daily()));}
 @Override public ResponseEntity<QuizResponse> createQuiz(QuizRequest r){return ResponseEntity.status(201).body(response(service.create(r.getTitle(),r.getPrompt(),r.getOptions(),r.getCorrectIndex(),r.getHint())));}
 @Override public ResponseEntity<AttemptResponse> startQuiz(UUID id,UUID user){return ResponseEntity.status(201).body(response(service.start(id,user)));}
 @Override public ResponseEntity<HintResponse> quizHint(UUID id){var v=service.hint(id);return ResponseEntity.ok(new HintResponse(v.text(),v.remaining()));}
 @Override public ResponseEntity<AttemptResponse> submitQuiz(UUID id,QuizSubmitRequest r){return ResponseEntity.ok(response(service.submit(id,r.getOptionIndex())));}
 private static QuizResponse response(Quiz v){return new QuizResponse(v.id(),v.title());}private static AttemptResponse response(Attempt v){return new AttemptResponse(v.id(),v.status(),v.hintsUsed()).score(v.score());}
}
