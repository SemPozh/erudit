package com.erudit.quiz;

import com.erudit.quiz.model.Quiz;
import com.erudit.quiz.model.QuizAnswerOption;
import com.erudit.quiz.model.QuizQuestion;
import com.erudit.quiz.repository.QuizRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompetitionIntegrationTest {

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

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private String createBody(String title, UUID quizId, Instant starts, Instant ends) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", title,
                "quizId", quizId.toString(),
                "rules", "Best attempt counts",
                "startsAt", starts.toString(),
                "endsAt", ends.toString()));
    }

    private ResultActions create(String username, String body) throws Exception {
        return mvc.perform(post("/api/v1/competition")
                .with(user(username))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Creates a competition that is running right now (window: 1h ago .. in 1h). */
    private UUID createActive(String username, Quiz quiz) throws Exception {
        String body = create(username,
                createBody("Cup", quiz.id(), now().minus(1, ChronoUnit.HOURS),
                        now().plus(1, ChronoUnit.HOURS)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).at("/data/id").asText());
    }

    private void addParticipant(UUID competitionId, String userId) {
        jdbc.update("""
                INSERT INTO competition_participants (competition_id, user_id, joined_at)
                VALUES (?, ?, ?)
                """, competitionId, userId, Timestamp.from(Instant.now()));
    }

    /** Plays the quiz through the normal quiz endpoints, all-correct or all-wrong. */
    private void play(Quiz quiz, String username, boolean correct) throws Exception {
        String started = mvc.perform(post("/api/v1/quiz/{id}/start", quiz.id()).with(user(username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(started).at("/data/id").asText();

        var answers = quiz.questions().stream()
                .map(q -> Map.of(
                        "questionId", q.id().toString(),
                        "answerId", q.answers().stream()
                                .filter(a -> a.correct() == correct)
                                .findFirst().orElseThrow().id().toString()))
                .toList();
        String body = objectMapper.writeValueAsString(
                Map.of("attemptId", attemptId, "answers", answers));

        mvc.perform(post("/api/v1/quiz/{id}/submit", quiz.id())
                        .with(user(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ---------- tests ----------

    @Test
    void createsCompetitionWithRulesAndReadsItById() throws Exception {
        Quiz quiz = saveQuiz();
        UUID id = createActive("comp-creator", quiz);

        mvc.perform(get("/api/v1/competition/{id}", id).with(user("comp-creator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.title").value("Cup"))
                .andExpect(jsonPath("$.data.quizId").value(quiz.id().toString()))
                .andExpect(jsonPath("$.data.rules").value("Best attempt counts"))
                .andExpect(jsonPath("$.data.creatorId").value("comp-creator"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.participantIds.length()").value(1))
                .andExpect(jsonPath("$.data.participantIds[0]").value("comp-creator"));
    }

    @Test
    void statusFollowsTheClock() throws Exception {
        Quiz quiz = saveQuiz();

        String scheduled = create("comp-status",
                createBody("Later", quiz.id(), now().plus(1, ChronoUnit.HOURS),
                        now().plus(2, ChronoUnit.HOURS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(scheduled).at("/data/id").asText());

        jdbc.update("UPDATE competitions SET starts_at = ?, ends_at = ? WHERE id = ?",
                Timestamp.from(now().minus(2, ChronoUnit.HOURS)),
                Timestamp.from(now().minus(1, ChronoUnit.HOURS)), id);

        mvc.perform(get("/api/v1/competition/{id}", id).with(user("comp-status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINISHED"));
    }

    @Test
    void rejectsInvalidCompetitions() throws Exception {
        Quiz quiz = saveQuiz();
        Instant start = now().plus(1, ChronoUnit.HOURS);

        // blank title
        create("comp-bad", createBody("  ", quiz.id(), start, start.plus(1, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest());
        // unknown quiz
        create("comp-bad", createBody("Cup", UUID.randomUUID(), start, start.plus(1, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest());
        // ends before it starts
        create("comp-bad", createBody("Cup", quiz.id(), start, start.minus(30, ChronoUnit.MINUTES)))
                .andExpect(status().isBadRequest());
        // already over
        create("comp-bad", createBody("Cup", quiz.id(), now().minus(2, ChronoUnit.HOURS),
                now().minus(1, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest());
        // missing required field
        create("comp-bad", "{\"title\":\"Cup\"}").andExpect(status().isBadRequest());

        // not authenticated
        mvc.perform(post("/api/v1/competition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Cup", quiz.id(), start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyParticipantsCanSeeCompetitionAndLeaderboard() throws Exception {
        Quiz quiz = saveQuiz();
        UUID id = createActive("comp-private-owner", quiz);

        mvc.perform(get("/api/v1/competition/{id}", id).with(user("comp-stranger")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/competition/{id}/leaderboard", id).with(user("comp-stranger")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/competition/{id}", UUID.randomUUID()).with(user("comp-private-owner")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/competition/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/competition/{id}/leaderboard", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void creatorAddsParticipantsAndInvitesFriendsIdempotently() throws Exception {
        UUID creator=createUser("owner"), friend=createUser("friend"), stranger=createUser("stranger");
        Quiz quiz=saveQuiz(); UUID competition=createActive(creator.toString(),quiz);
        jdbc.update("insert into friend_requests values (?,?,?,'ACCEPTED',?,?)",UUID.randomUUID(),creator,friend,Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));
        String body="{\"userId\":\""+friend+"\"}";
        mvc.perform(post("/api/v1/competition/{id}/participants",competition).with(user(stranger.toString())).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/competition/{id}/participants",competition).with(user(creator.toString())).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.participantIds.length()").value(2));
        mvc.perform(post("/api/v1/competition/{id}/participants",competition).with(user(creator.toString())).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.participantIds.length()").value(2));
        mvc.perform(post("/api/v1/competition/{id}/invitations",competition).with(user(creator.toString())).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/competition/{id}/invitations",competition).with(user(creator.toString())).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from competition_invitations where competition_id=?",Integer.class,competition)).isEqualTo(1);
    }

    private UUID createUser(String name) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,name,created_at,status,role,email_verified) values (?,?,?,?,?,'ACTIVE','USER',true)",id,name+id+"@test.local","hash",name,java.time.LocalDateTime.now());
        jdbc.update("insert into user_settings(user_id) values (?)",id);
        return id;
    }
}
