package com.erudit.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class QuizRepositoryIntegrationTest {
    @Autowired private QuizRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void savesAndReadsQuizAggregateWithContentAndCardLinks() {
        QuizTestFixture.StoredContent content = QuizTestFixture.contentWithCard(jdbc);
        Quiz quiz = QuizTestFixture.quiz(content);

        repository.save(quiz);

        assertThat(repository.findById(quiz.id())).contains(quiz);
        assertThat(repository.findByContentId(content.contentId())).containsExactly(quiz);
    }

    @Test
    void rejectsQuizCardFromDifferentContent() {
        QuizTestFixture.StoredContent quizContent = QuizTestFixture.contentWithCard(jdbc);
        QuizTestFixture.StoredContent otherContent = QuizTestFixture.contentWithCard(jdbc);
        Quiz quiz = QuizTestFixture.quiz(new QuizTestFixture.StoredContent(
                quizContent.contentId(), otherContent.cardId()));

        assertThatThrownBy(() -> repository.save(quiz))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quiz content");
        assertThat(repository.findById(quiz.id())).isEmpty();
    }
}
