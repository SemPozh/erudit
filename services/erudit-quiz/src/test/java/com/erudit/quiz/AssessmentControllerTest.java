package com.erudit.quiz;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
import com.erudit.quiz.controller.AssessmentController;import com.erudit.quiz.model.Assessment.*;import com.erudit.quiz.openapi.model.*;import com.erudit.quiz.service.AssessmentService;
import java.util.*;import org.junit.jupiter.api.Test;
class AssessmentControllerTest {
 @Test void exposesAssessmentAndProgramFlow(){var service=mock(AssessmentService.class);var controller=new AssessmentController(service);UUID user=UUID.randomUUID(),session=UUID.randomUUID(),question=UUID.randomUUID();when(service.questions()).thenReturn(List.of(new Question(question,"Q",List.of("A"),0)));when(service.start(user)).thenReturn(new Session(session,user,"STARTED",null,null));when(service.submit(eq(session),any())).thenReturn(new Session(session,user,"COMPLETED",1,"ADVANCED"));when(service.get(session)).thenReturn(new Session(session,user,"COMPLETED",1,"ADVANCED"));when(service.program(user)).thenReturn(new Program(user,"ADVANCED",0));when(service.next(user)).thenReturn(new AssessmentService.Step("BASICS","Start"));
  assertThat(controller.listAssessmentQuestions().getBody()).hasSize(1);assertThat(controller.startAssessment(user).getStatusCode().value()).isEqualTo(201);var answer=new AnswerRequest(question,0);assertThat(controller.submitAssessment(session,new SubmitRequest(List.of(answer))).getBody().getScore()).isOne();controller.getAssessmentResult(session);controller.getLearningProgram(user);assertThat(controller.getNextLearningStep(user).getBody().getCode()).isEqualTo("BASICS");
 }
}
