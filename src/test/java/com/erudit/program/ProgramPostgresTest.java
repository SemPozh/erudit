package com.erudit.program;

import com.erudit.program.model.LearningProgram;
import com.erudit.program.model.LessonStatus;
import com.erudit.program.model.ProgramLesson;
import com.erudit.program.model.ProgramModule;
import com.erudit.program.repository.ProgramRepository;

import com.erudit.content.repository.ContentRepository;
import com.erudit.content.model.Difficulty;
import com.erudit.quiz.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "clickhouse.enabled=false")
@Testcontainers
class ProgramPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private ProgramRepository repository;
    @Autowired private ContentRepository contentRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void flywaySchemaStoresProgramAndCalculatesProgressOnPostgres() {
        String userId = "postgres-program-student";
        ProgramTestFixture.LessonIds source = ProgramTestFixture.lesson(
                contentRepository, quizRepository, "SCIENCE", Difficulty.BEGINNER);
        ProgramLesson lesson = new ProgramLesson(UUID.randomUUID(), source.contentId(), source.quizId(),
                0, LessonStatus.NOT_STARTED);
        ProgramModule module = new ProgramModule(UUID.randomUUID(), "SCIENCE", "Наука", 0, 0, List.of(lesson));
        LearningProgram program = new LearningProgram(UUID.randomUUID(), userId, Instant.now(), 0, List.of(module));
        repository.replace(userId, program);
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO content_progress (user_id, content_id, status, viewed_at, completed_at)
                VALUES (?, ?, 'COMPLETED', ?, ?)
                """, userId, source.contentId(), Timestamp.from(now), Timestamp.from(now));

        LearningProgram stored = repository.findByUserId(userId).orElseThrow();

        assertThat(stored.progressPercent()).isEqualTo(100.0);
        assertThat(stored.modules().getFirst().lessons().getFirst().status()).isEqualTo(LessonStatus.COMPLETED);
    }
}
