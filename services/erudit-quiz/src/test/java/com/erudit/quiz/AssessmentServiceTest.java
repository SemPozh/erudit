package com.erudit.quiz;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.erudit.quiz.model.Assessment.*;
import com.erudit.quiz.repository.AssessmentRepository;
import com.erudit.quiz.service.AssessmentService;
import java.util.*;
import org.junit.jupiter.api.Test;
class AssessmentServiceTest {
 private final AssessmentRepository repository=mock(AssessmentRepository.class);private final AssessmentService service=new AssessmentService(repository);
 @Test void scoresAndCreatesProgramOnce(){UUID sessionId=UUID.randomUUID(),user=UUID.randomUUID(),question=UUID.randomUUID();when(repository.session(sessionId)).thenReturn(Optional.of(new Session(sessionId,user,"STARTED",null,null)));when(repository.questions()).thenReturn(List.of(new Question(question,"Q",List.of("A","B"),1)));when(repository.finish(any())).thenAnswer(i->i.getArgument(0));when(repository.saveProgram(any())).thenAnswer(i->i.getArgument(0));var result=service.submit(sessionId,List.of(new Answer(question,1)));assertThat(result.score()).isOne();assertThat(result.grade()).isEqualTo("ADVANCED");verify(repository).saveProgram(new Program(user,"ADVANCED",0));}
 @Test void rejectsRepeatAndReturnsNextStep(){UUID sessionId=UUID.randomUUID(),user=UUID.randomUUID(),demo=UUID.fromString("00000000-0000-0000-0000-000000000001");when(repository.session(sessionId)).thenReturn(Optional.of(new Session(sessionId,user,"COMPLETED",1,"ADVANCED")));assertThatThrownBy(()->service.submit(sessionId,List.of())).isInstanceOf(IllegalStateException.class);when(repository.program(user)).thenReturn(Optional.of(new Program(user,"ADVANCED",0)));when(repository.start(demo)).thenReturn(new Session(sessionId,demo,"STARTED",null,null));assertThat(service.next(user).code()).isEqualTo("BASICS");assertThat(service.start(null).userId()).isEqualTo(demo);}
}
