package com.erudit.payment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.erudit.integration.InternalClients.UserClient;
import com.erudit.integration.InternalContracts.UserLookup;
import com.erudit.payment.model.Subscription;
import com.erudit.payment.repository.PaymentRepository;
import com.erudit.payment.service.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
class SubscriptionServiceTest {
    @Test void accessIsValidOnlyInsideThePaidPeriod() {
        var start = Instant.parse("2026-11-04T10:00:00Z");
        var s = new Subscription(UUID.randomUUID(),UUID.randomUUID(),"PREMIUM_MONTHLY","ACTIVE",start,start.plusSeconds(60));
        assertThat(s.activeAt(start.minusNanos(1))).isFalse();
        assertThat(s.activeAt(start)).isTrue();
        assertThat(s.activeAt(start.plusSeconds(60))).isFalse();
        assertThat(new Subscription(s.id(),s.userId(),s.planCode(),"EXPIRED",s.startDate(),s.endDate()).activeAt(start)).isFalse();
    }
    @Test void invalidOrMissingRemoteUsersNeverReachTheDatabase() {
        var repository = mock(PaymentRepository.class); var users = mock(UserClient.class);
        var service = new SubscriptionService(repository,users,Clock.systemUTC(),mock(TransactionTemplate.class));
        UUID user = UUID.randomUUID();
        for (var lookup : java.util.List.of(new UserLookup(user,"Deleted",false),new UserLookup(UUID.randomUUID(),"Wrong",true))) {
            when(users.get(user)).thenReturn(lookup);
            assertThatThrownBy(() -> service.create(user,"PREMIUM_MONTHLY","mock"))
                    .isInstanceOfSatisfying(PaymentException.class,e -> assertThat(e.status()).isEqualTo(404));
        }
        when(users.get(user)).thenReturn(null);
        assertThatThrownBy(() -> service.create(user,"PREMIUM_MONTHLY","mock")).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> service.create(user,null,"mock")).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> service.create(user,"plan"," ")).isInstanceOf(PaymentException.class);
        var missing = mock(feign.FeignException.NotFound.class);
        when(users.get(user)).thenThrow(new RuntimeException("circuit breaker wrapper", missing));
        assertThatThrownBy(() -> service.create(user,"PREMIUM_MONTHLY","mock"))
                .isInstanceOfSatisfying(PaymentException.class,e -> assertThat(e.status()).isEqualTo(404));
        verifyNoInteractions(repository);
    }
}
