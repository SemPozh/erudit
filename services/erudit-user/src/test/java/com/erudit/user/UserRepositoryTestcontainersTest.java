package com.erudit.user;
import static org.assertj.core.api.Assertions.*;
import com.erudit.user.model.UserSettings;
import com.erudit.user.repository.UserRepository;
import java.nio.file.*;import java.time.OffsetDateTime;
import org.junit.jupiter.api.*;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;import org.testcontainers.postgresql.PostgreSQLContainer;
@Testcontainers class UserRepositoryTestcontainersTest {
 @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17-alpine");static UserRepository repository;
 @BeforeAll static void setup()throws Exception{var jdbc=JdbcClient.create(new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()));for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V1__users.sql")).split(";"))if(!sql.isBlank())jdbc.sql(sql).update();repository=new UserRepository(jdbc);}
 @Test void coversCrudSearchVerificationAndSettings(){var user=repository.create("new@example.com","New","token",OffsetDateTime.now().plusHours(1));assertThat(repository.get(user.id())).isPresent();assertThat(repository.search("New",0,20)).hasSize(1);assertThat(repository.count("New")).isOne();assertThat(repository.verify("token")).isTrue();assertThat(repository.update(user.id(),"Updated").displayName()).isEqualTo("Updated");assertThat(repository.settings(user.id()).locale()).isEqualTo("ru");assertThat(repository.save(new UserSettings(user.id(),"en",20)).dailyGoal()).isEqualTo(20);repository.delete(user.id());assertThat(repository.get(user.id())).isEmpty();}
}
