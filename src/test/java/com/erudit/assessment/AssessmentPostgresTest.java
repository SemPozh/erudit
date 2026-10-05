package com.erudit.assessment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "clickhouse.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class AssessmentPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private AssessmentRepository repository;

    @Test
    void savesTopicScoresAndUpdatesAssessmentProfileOnPostgres() {
        AssessmentQuestion question = repository.findActiveQuestions().getFirst();
        AssessmentOption correct = question.answers().stream().filter(AssessmentOption::correct).findFirst().orElseThrow();
        UUID assessmentId = UUID.randomUUID();
        repository.createSession(assessmentId, "postgres-student", Instant.now(), List.of(question));
        AssessmentSubmission result = new AssessmentSubmission(UUID.randomUUID(), 1, 1, 2000,
                List.of(new TopicAssessmentScore(question.topic(), 1, 1, 2000)),
                new Grade("LEGEND_III", "Легенда III", 2000, 2000));

        repository.submit(assessmentId, "postgres-student",
                List.of(new SubmittedAnswer(question.id(), correct.id())), Map.of(correct.id(), correct),
                Instant.now(), result);

        assertThat(repository.findLatestResult("postgres-student")).contains(result);
    }
}
