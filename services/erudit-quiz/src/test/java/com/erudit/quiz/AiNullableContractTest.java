package com.erudit.quiz;

import com.erudit.quiz.repository.QuizRepository;
import com.erudit.quiz.service.QuizService;
import com.erudit.integration.InternalClients.AiClient;
import com.erudit.integration.InternalContracts.AiQuiz;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.mockito.Mockito.*;

class AiNullableContractTest {
    @Test void missingOrBlankAiFieldsNeverReachTheQuizRepository() {
        var ai=mock(AiClient.class);var repository=mock(QuizRepository.class);
        var beans=new StaticListableBeanFactory(Map.of("ai",ai));
        var service=new QuizService(repository,beans.getBeanProvider(AiClient.class),CircuitBreakerRegistry.ofDefaults());
        for(var malformed:List.of(new AiQuiz(null,List.of("A","B"),0),new AiQuiz("",List.of("A","B"),0),
                new AiQuiz("Question",Arrays.asList(null,"B"),0),new AiQuiz("Question",List.of("","B"),0))){
            when(ai.quiz(any())).thenReturn(malformed);
            service.create("Local","Local question",List.of("A","B"),0,"Hint");
        }
        verify(repository,times(4)).create("Local","Local question",List.of("A","B"),0,"Hint");
        verifyNoMoreInteractions(repository);
    }
}
