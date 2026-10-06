package com.erudit.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "clickhouse.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class QuizPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private QuizRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void flywaySchemaPersistsCompleteQuizOnPostgres() {
        Quiz quiz = QuizTestFixture.quiz(QuizTestFixture.contentWithCard(jdbc));

        repository.save(quiz);

        assertThat(repository.findById(quiz.id())).contains(quiz);
    }
}
