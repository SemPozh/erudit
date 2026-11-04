package com.erudit.notificator;
import static org.assertj.core.api.Assertions.assertThat;
import com.erudit.notificator.model.*;
import com.erudit.notificator.repository.NotificationRepository;
import com.erudit.notificator.service.NotificationService;
import java.nio.file.*;import java.util.*;
import org.junit.jupiter.api.*;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;import org.testcontainers.postgresql.PostgreSQLContainer;import reactor.test.StepVerifier;
@Testcontainers class NotificationPostgresTest{
 @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");static NotificationService service;
 @BeforeAll static void setup()throws Exception{var ds=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var jdbc=JdbcClient.create(ds);for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V1__notifications.sql")).split(";")){if(!sql.isBlank())jdbc.sql(sql).update();}service=new NotificationService(new NotificationRepository(jdbc),2,32);}
 @Test void coversPreferencesInboxPaginationAndReadTransitions(){UUID user=UUID.randomUUID();
  StepVerifier.create(service.savePreference(new Preference(user,true,List.of("IN_APP","PUSH")))).assertNext(v->assertThat(v.channels()).contains("PUSH")).verifyComplete();
  StepVerifier.create(service.enqueue(user,"DEMO",null,null)).assertNext(n->assertThat(n.read()).isFalse()).verifyComplete();
  StepVerifier.create(service.list(user,0,20)).assertNext(p->assertThat(p.total()).isEqualTo(1)).verifyComplete();
  StepVerifier.create(service.unread(user)).expectNext(1L).verifyComplete();
  service.readAll(user).block();StepVerifier.create(service.unread(user)).expectNext(0L).verifyComplete();
 }
}
