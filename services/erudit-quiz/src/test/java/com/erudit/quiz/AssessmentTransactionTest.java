package com.erudit.quiz.config;

import com.erudit.quiz.application.port.AssessmentRepository;
import com.erudit.quiz.config.UseCaseConfiguration;
import com.erudit.quiz.domain.Assessment.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AssessmentTransactionTest {
    @Test void programFailureRollsBackTheAssessmentResult() {
        var repository = mock(AssessmentRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        UUID session = UUID.randomUUID(), user = UUID.randomUUID(), question = UUID.randomUUID();
        when(repository.session(session)).thenReturn(Optional.of(new Session(session, user, "STARTED", null, null)));
        when(repository.questions()).thenReturn(List.of(new Question(question, "Q", List.of("A", "B"), 0)));
        when(repository.saveProgram(any())).thenThrow(new IllegalStateException("Program write failed"));
        var service = new UseCaseConfiguration().assessmentService(repository, transactions);
        assertThatThrownBy(() -> service.submit(session, List.of(new Answer(question, 0))))
                .isInstanceOf(IllegalStateException.class).hasMessage("Program write failed");
        var order = inOrder(transactions, repository);
        order.verify(transactions).getTransaction(any());
        order.verify(repository).session(session);
        order.verify(repository).questions();
        order.verify(repository).finish(new Session(session, user, "COMPLETED", 1, "ADVANCED"));
        order.verify(repository).saveProgram(new Program(user, "ADVANCED", 0));
        order.verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }
}
