package com.erudit.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuizIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private QuizRepository quizRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- helpers ----------

    private Quiz saveQuiz() {
        QuizTestFixture.StoredContent content = QuizTestFixture.contentWithCard(jdbc);

        var q1 = new QuizQuestion(UUID.randomUUID(), 0, "Question 1?", content.cardId(), List.of(
                new QuizAnswerOption(UUID.randomUUID(), 0, "right", true),
                new QuizAnswerOption(UUID.randomUUID(), 1, "wrong", false)));
        var q2 = new QuizQuestion(UUID.randomUUID(), 1, "Question 2?", content.cardId(), List.of(
                new QuizAnswerOption(UUID.randomUUID(), 0, "wrong", false),
                new QuizAnswerOption(UUID.randomUUID(), 1, "right", true)));

        var quiz = new Quiz(UUID.randomUUID(), content.contentId(), "Test quiz",
                Instant.now(), List.of(q1, q2));
        quizRepository.save(quiz);
        return quiz;
    }

    private UUID start(UUID quizId, String username) throws Exception {
        String body = mvc.perform(post("/api/v1/quiz/{id}/start", quizId).with(user(username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).at("/data/id").asText());
    }

    /** One answer per question; either all correct or all wrong. */
    private String submitBody(UUID attemptId, Quiz quiz, boolean correct) throws Exception {
        var answers = quiz.questions().stream()
                .map(q -> Map.of(
                        "questionId", q.id().toString(),
                        "answerId", q.answers().stream()
                                .filter(a -> a.correct() == correct)
                                .findFirst().orElseThrow().id().toString()))
                .toList();
        return objectMapper.writeValueAsString(
                Map.of("attemptId", attemptId.toString(), "answers", answers));
    }

    private org.springframework.test.web.servlet.ResultActions submit(
            UUID quizId, String username, String body) throws Exception {
        return mvc.perform(post("/api/v1/quiz/{id}/submit", quizId)
                .with(user(username))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // ---------- tests ----------

    @Test
    void startReturnsQuestionAndAnswerIdsWithoutLeakingCorrectAnswers() throws Exception {
        Quiz quiz = saveQuiz();

        String body = mvc.perform(post("/api/v1/quiz/{id}/start", quiz.id()).with(user("quiz-user-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quizId").value(quiz.id().toString()))
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[0].id")
                        .value(quiz.questions().get(0).id().toString()))
                .andExpect(jsonPath("$.data.questions[0].answers[0].id")
                        .value(quiz.questions().get(0).answers().get(0).id().toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("correct");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM quiz_attempts WHERE quiz_id = ? AND submitted_at IS NULL",
                Integer.class, quiz.id())).isEqualTo(1);
    }

    @Test
    void perfectSubmissionGivesFullScoreAndCompletionBonus() throws Exception {
        Quiz quiz = saveQuiz();
        UUID attemptId = start(quiz.id(), "quiz-user-2");

        submit(quiz.id(), "quiz-user-2", submitBody(attemptId, quiz, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.data.correctAnswers").value(2))
                .andExpect(jsonPath("$.data.score").value(2))
                .andExpect(jsonPath("$.data.experience").value(3));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM quiz_attempt_answers WHERE attempt_id = ?",
                Integer.class, attemptId)).isEqualTo(2);
    }

    @Test
    void allWrongStillGetsCompletionBonus() throws Exception {
        Quiz quiz = saveQuiz();
        UUID attemptId = start(quiz.id(), "quiz-user-3");

        submit(quiz.id(), "quiz-user-3", submitBody(attemptId, quiz, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctAnswers").value(0))
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.experience").value(1));
    }

    @Test
    void repeatingTheQuizStoresANewAttempt() throws Exception {
        Quiz quiz = saveQuiz();

        UUID first = start(quiz.id(), "quiz-user-4");
        submit(quiz.id(), "quiz-user-4", submitBody(first, quiz, true)).andExpect(status().isOk());

        UUID second = start(quiz.id(), "quiz-user-4");
        submit(quiz.id(), "quiz-user-4", submitBody(second, quiz, false)).andExpect(status().isOk());

        assertThat(first).isNotEqualTo(second);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM quiz_attempts WHERE quiz_id = ? AND user_id = ? AND submitted_at IS NOT NULL",
                Integer.class, quiz.id(), "quiz-user-4")).isEqualTo(2);
    }

    @Test
    void rejectsInvalidSubmissions() throws Exception {
        Quiz quiz = saveQuiz();
        QuizQuestion q1 = quiz.questions().get(0);
        QuizQuestion q2 = quiz.questions().get(1);
        UUID attemptId = start(quiz.id(), "quiz-user-5");

        // missing a question
        String missing = objectMapper.writeValueAsString(Map.of(
                "attemptId", attemptId.toString(),
                "answers", List.of(Map.of(
                        "questionId", q1.id().toString(),
                        "answerId", q1.answers().get(0).id().toString()))));
        submit(quiz.id(), "quiz-user-5", missing)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        // same question twice
        var q1Answer = Map.of("questionId", q1.id().toString(),
                "answerId", q1.answers().get(0).id().toString());
        String duplicate = objectMapper.writeValueAsString(Map.of(
                "attemptId", attemptId.toString(),
                "answers", List.of(q1Answer, q1Answer)));
        submit(quiz.id(), "quiz-user-5", duplicate).andExpect(status().isBadRequest());

        // answer that belongs to a different question
        String foreignAnswer = objectMapper.writeValueAsString(Map.of(
                "attemptId", attemptId.toString(),
                "answers", List.of(
                        Map.of("questionId", q1.id().toString(),
                                "answerId", q2.answers().get(0).id().toString()),
                        Map.of("questionId", q2.id().toString(),
                                "answerId", q2.answers().get(1).id().toString()))));
        submit(quiz.id(), "quiz-user-5", foreignAnswer).andExpect(status().isBadRequest());

        // nothing above should have been saved
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM quiz_attempts WHERE id = ? AND submitted_at IS NULL",
                Integer.class, attemptId)).isEqualTo(1);
    }

    @Test
    void rejectsAttemptFromAnotherQuiz() throws Exception {
        Quiz quizA = saveQuiz();
        Quiz quizB = saveQuiz();
        UUID attemptOnA = start(quizA.id(), "quiz-user-6");

        submit(quizB.id(), "quiz-user-6", submitBody(attemptOnA, quizB, true))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSecondSubmissionOfSameAttempt() throws Exception {
        Quiz quiz = saveQuiz();
        UUID attemptId = start(quiz.id(), "quiz-user-7");
        String body = submitBody(attemptId, quiz, true);

        submit(quiz.id(), "quiz-user-7", body).andExpect(status().isOk());
        submit(quiz.id(), "quiz-user-7", body).andExpect(status().isBadRequest());
    }

    @Test
    void doesNotLetAnotherUserSubmitSomeoneElsesAttempt() throws Exception {
        Quiz quiz = saveQuiz();
        UUID attemptId = start(quiz.id(), "quiz-owner");

        submit(quiz.id(), "quiz-intruder", submitBody(attemptId, quiz, true))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthenticationAndExistingQuiz() throws Exception {
        Quiz quiz = saveQuiz();

        mvc.perform(post("/api/v1/quiz/{id}/start", quiz.id()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/quiz/{id}/start", UUID.randomUUID()).with(user("quiz-user-8")))
                .andExpect(status().isNotFound());
    }
}