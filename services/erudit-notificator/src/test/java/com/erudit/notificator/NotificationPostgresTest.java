package com.erudit.notificator;
import static org.assertj.core.api.Assertions.assertThat;
import com.erudit.notificator.model.*;
import com.erudit.notificator.repository.NotificationRepository;
import com.erudit.notificator.repository.DeliveryRepository;
import com.erudit.notificator.service.NotificationService;
import java.nio.file.*;import java.util.*;
import org.junit.jupiter.api.*;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;import org.testcontainers.postgresql.PostgreSQLContainer;import reactor.test.StepVerifier;
@Testcontainers class NotificationPostgresTest{
 @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");static NotificationService service;static DeliveryRepository deliveries;
 @BeforeAll static void setup()throws Exception{var ds=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var jdbc=JdbcClient.create(ds);for(String file:List.of("V1__notifications.sql","V2__mock_delivery.sql"))for(String sql:Files.readString(Path.of("src/main/resources/db/migration/"+file)).split(";")){if(!sql.isBlank())jdbc.sql(sql).update();}deliveries=new DeliveryRepository(jdbc);service=new NotificationService(new NotificationRepository(jdbc),deliveries,2,32);}
 @Test void coversPreferencesInboxPaginationAndReadTransitions(){UUID user=UUID.randomUUID();
  StepVerifier.create(service.savePreference(new Preference(user,true,List.of("IN_APP","PUSH")))).assertNext(v->assertThat(v.channels()).contains("PUSH")).verifyComplete();
  StepVerifier.create(service.enqueue(user,"DEMO",null,null)).assertNext(n->assertThat(n.read()).isFalse()).verifyComplete();
  StepVerifier.create(service.list(user,0,20)).assertNext(p->assertThat(p.total()).isEqualTo(1)).verifyComplete();
  StepVerifier.create(service.unread(user)).expectNext(1L).verifyComplete();
  service.readAll(user).block();StepVerifier.create(service.unread(user)).expectNext(0L).verifyComplete();
 }
 @Test void storesOnlyMockDeliveryAttempts(){UUID user=UUID.randomUUID();var notification=service.enqueue(user,"DEMO",null,null).block();assertThat(deliveries.pending(10)).extracting(NotificationRecord::id).contains(notification.id());var attempt=deliveries.record(notification,"MOCK_DELIVERED");assertThat(attempt.payloadPreview()).contains("Erudit update");assertThat(deliveries.deliveredToday(user)).isOne();assertThat(deliveries.attempts(notification.id())).extracting(DeliveryAttempt::status).containsExactly("MOCK_DELIVERED");assertThat(deliveries.pending(10)).extracting(NotificationRecord::id).doesNotContain(notification.id());}
 @Test void coversPreferenceDefaultsTemplateCrudAndIndividualReads(){
  UUID user=UUID.randomUUID(),templateId=UUID.randomUUID();String code="TEST_"+UUID.randomUUID().toString().replace("-","");
  StepVerifier.create(service.preference(user)).assertNext(v->{assertThat(v.enabled()).isTrue();assertThat(v.channels()).containsExactly("IN_APP");}).verifyComplete();
  var created=new Template(templateId,code,"Initial title","Initial body",true);
  StepVerifier.create(service.saveTemplate(created)).expectNext(created).verifyComplete();
  StepVerifier.create(service.templates().collectList()).assertNext(v->assertThat(v).extracting(Template::id).contains(templateId)).verifyComplete();
  StepVerifier.create(service.template(templateId)).expectNext(created).verifyComplete();
  var updated=new Template(templateId,code,"Updated title","Updated body",true);
  StepVerifier.create(service.updateTemplate(updated)).expectNext(updated).verifyComplete();
  var notification=service.enqueue(user,code,"Custom title","Custom body").block();
  assertThat(notification.title()).isEqualTo("Custom title");
  StepVerifier.create(service.read(user,notification.id())).verifyComplete();
  StepVerifier.create(service.read(user,UUID.randomUUID())).expectError(IllegalArgumentException.class).verify();
  deliveries.record(notification,"MOCK_DELIVERED");
  StepVerifier.create(service.attempts(notification.id())).expectNextCount(1).verifyComplete();
  StepVerifier.create(service.deleteTemplate(templateId)).verifyComplete();
  StepVerifier.create(service.template(templateId)).expectError(IllegalArgumentException.class).verify();
  StepVerifier.create(service.deleteTemplate(templateId)).expectError(IllegalArgumentException.class).verify();
 }
}
