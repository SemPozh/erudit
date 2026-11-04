package com.erudit.notificator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.erudit.notificator.model.*;
import com.erudit.notificator.repository.NotificationRepository;
import com.erudit.notificator.service.NotificationService;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
class NotificationServiceTest{
 @Test void jdbcRunsOutsideEventLoopAndSupportsTransitions(){
  var repository=mock(NotificationRepository.class);var thread=new AtomicReference<String>();UUID user=UUID.randomUUID(),id=UUID.randomUUID();
  when(repository.unread(user)).thenAnswer(i->{thread.set(Thread.currentThread().getName());return 2L;});
  when(repository.read(user,id)).thenReturn(true);var service=new NotificationService(repository,2,16);
  StepVerifier.create(service.unread(user)).expectNext(2L).verifyComplete();assertThat(thread.get()).startsWith("notification-jdbc");
  StepVerifier.create(service.read(user,id)).verifyComplete();verify(repository).read(user,id);
 }
 @Test void paginatesAndEnqueuesWithoutExternalDelivery(){
  var repository=mock(NotificationRepository.class);UUID user=UUID.randomUUID();var template=new Template(UUID.randomUUID(),"DEMO","Title","Body",true);
  when(repository.template("DEMO")).thenReturn(Optional.of(template));when(repository.insert(any(),any(),any(),any())).thenAnswer(i->new NotificationRecord(UUID.randomUUID(),i.getArgument(0),i.getArgument(1),i.getArgument(2),i.getArgument(3),false,OffsetDateTime.now()));
  when(repository.list(user,0,20)).thenReturn(List.of());when(repository.count(user)).thenReturn(0L);var service=new NotificationService(repository,2,16);
  StepVerifier.create(service.list(user,0,20)).assertNext(p->assertThat(p.total()).isZero()).verifyComplete();
  StepVerifier.create(service.enqueue(user,"DEMO",null,null)).assertNext(n->assertThat(n.title()).isEqualTo("Title")).verifyComplete();
  StepVerifier.create(service.enqueue(Collections.nCopies(101,new NotificationService.Enqueue(user,"DEMO",null,null)))).expectError(IllegalArgumentException.class).verify();
 }
}
