package com.erudit.user;
import static org.assertj.core.api.Assertions.*;
import com.erudit.user.model.UserSettings;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.repository.SocialRepository;
import java.nio.file.*;import java.time.OffsetDateTime;
import org.junit.jupiter.api.*;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;import org.testcontainers.postgresql.PostgreSQLContainer;
@Testcontainers class UserRepositoryTestcontainersTest {
 @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17-alpine");static UserRepository repository;static SocialRepository social;
 @BeforeAll static void setup()throws Exception{var jdbc=JdbcClient.create(new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()));for(String file:java.util.List.of("V1__users.sql","V2__friends_and_devices.sql"))for(String sql:Files.readString(Path.of("src/main/resources/db/migration/"+file)).split(";"))if(!sql.isBlank())jdbc.sql(sql).update();repository=new UserRepository(jdbc);social=new SocialRepository(jdbc);}
 @Test void coversCrudSearchVerificationAndSettings(){var user=repository.create("new@example.com","New","token",OffsetDateTime.now().plusHours(1));assertThat(repository.get(user.id())).isPresent();assertThat(repository.search("New",0,20)).hasSize(1);assertThat(repository.count("New")).isOne();assertThat(repository.verify("token")).isTrue();assertThat(repository.update(user.id(),"Updated").displayName()).isEqualTo("Updated");assertThat(repository.settings(user.id()).locale()).isEqualTo("ru");assertThat(repository.save(new UserSettings(user.id(),"en",20)).dailyGoal()).isEqualTo(20);repository.delete(user.id());assertThat(repository.get(user.id())).isEmpty();}
 @Test void coversIdempotentFriendsAndDevices(){var a=repository.create("a@example.com","A","a",OffsetDateTime.now().plusHours(1));var b=repository.create("b@example.com","B","b",OffsetDateTime.now().plusHours(1));var request=social.request(a.id(),b.id());assertThat(social.request(a.id(),b.id())).isEqualTo(request);social.decide(b.id(),request,true);assertThat(social.friends(a.id(),0,20)).hasSize(1);var device=social.saveDevice(a.id(),"push-token-a","ANDROID");assertThat(social.saveDevice(a.id(),"push-token-a","ANDROID").id()).isEqualTo(device.id());assertThat(social.internal(a.id()).deviceIds()).contains(device.id());social.revokeDevice(a.id(),device.id());social.remove(a.id(),b.id());}
}
