package com.erudit.quiz;
import static org.assertj.core.api.Assertions.*;
import com.erudit.quiz.model.Assessment.*;import com.erudit.quiz.repository.AssessmentRepository;
import java.nio.file.*;import java.util.UUID;
import org.junit.jupiter.api.*;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;import org.testcontainers.postgresql.PostgreSQLContainer;
@Testcontainers class AssessmentRepositoryTestcontainersTest {
 @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17-alpine");static AssessmentRepository repository;
 @BeforeAll static void setup()throws Exception{var jdbc=JdbcClient.create(new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()));for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V1__assessment.sql")).split(";"))if(!sql.isBlank())jdbc.sql(sql).update();repository=new AssessmentRepository(jdbc);}
 @Test void coversAssessmentAndProgramPersistence(){UUID user=UUID.randomUUID();assertThat(repository.questions()).hasSize(2);var started=repository.start(user);assertThat(repository.session(started.id())).isPresent();var done=repository.finish(new Session(started.id(),user,"COMPLETED",2,"ADVANCED"));assertThat(done.score()).isEqualTo(2);repository.saveProgram(new Program(user,"ADVANCED",0));assertThat(repository.program(user)).contains(new Program(user,"ADVANCED",0));assertThatThrownBy(()->repository.finish(done)).isInstanceOf(IllegalStateException.class);}
}
