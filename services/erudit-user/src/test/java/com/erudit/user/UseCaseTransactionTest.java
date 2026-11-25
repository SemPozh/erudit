package com.erudit.user.config;

import com.erudit.user.application.port.*;
import com.erudit.user.config.UseCaseConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UseCaseTransactionTest {
    @Test void registrationCommitsAndFailedFriendDecisionRollsBack() {
        var transactions = mock(PlatformTransactionManager.class);
        var registration = new SimpleTransactionStatus();
        var decision = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(registration, decision);
        var users = mock(UserRepository.class);
        var passwords = mock(Passwords.class);
        var social = mock(SocialRepository.class);
        var configuration = new UseCaseConfiguration();
        configuration.userService(users, passwords, transactions).register("a@example.com", "A", "password", "LEARNER");
        verify(transactions).commit(registration);
        UUID owner = UUID.randomUUID(), request = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Request not found")).when(social).decide(owner, request, true);
        assertThatThrownBy(() -> configuration.socialService(social, transactions).decide(owner, request, "accept"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(transactions).rollback(decision);
        verify(transactions, times(2)).getTransaction(argThat(definition ->
                !definition.isReadOnly() && definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
    }
}
