package com.erudit.program;

import com.erudit.content.ContentRepository;
import com.erudit.content.Difficulty;
import com.erudit.quiz.QuizRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgramIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ContentRepository contentRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesPersonalProgramAndReflectsContentProgress() throws Exception {
        String userId = "program-student";
        completeAssessment(userId);
        ProgramTestFixture.LessonIds culture = ProgramTestFixture.lesson(
                contentRepository, quizRepository, "CULTURE", Difficulty.BEGINNER);

        mvc.perform(post("/api/v1/program/generate").with(user(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.modules.length()").value(1))
                .andExpect(jsonPath("$.data.modules[0].topic").value("CULTURE"))
                .andExpect(jsonPath("$.data.modules[0].lessons[0].contentId")
                        .value(culture.contentId().toString()))
                .andExpect(jsonPath("$.data.progressPercent").value(0.0));

        mvc.perform(get("/api/v1/program/next").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentId").value(culture.contentId().toString()))
                .andExpect(jsonPath("$.data.reason").value("CONTINUE_PROGRAM"))
                .andExpect(jsonPath("$.data.streakDays").value(0))
                .andExpect(jsonPath("$.data.dailyGoalProgress").value(0.0));

        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO content_progress (user_id, content_id, status, viewed_at, completed_at)
                VALUES (?, ?, 'COMPLETED', ?, ?)
                """, userId, culture.contentId(), Timestamp.from(now), Timestamp.from(now));

        mvc.perform(get("/api/v1/program/progress").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercent").value(100.0))
                .andExpect(jsonPath("$.data.modules[0].progressPercent").value(100.0))
                .andExpect(jsonPath("$.data.modules[0].lessons[0].status").value("COMPLETED"));
        mvc.perform(get("/api/v1/program/me").with(user(userId)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/program/next").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("DAILY_GOAL_COMPLETE"))
                .andExpect(jsonPath("$.data.streakDays").value(1));
    }

    @Test
    void requiresAuthenticationAndAssessmentResult() throws Exception {
        mvc.perform(post("/api/v1/program/generate"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/program/generate").with(user("student-without-assessment")))
                .andExpect(status().isNotFound());
    }

    private void completeAssessment(String userId) throws Exception {
        String started = mvc.perform(post("/api/v1/assessment/start").with(user(userId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String assessmentId = objectMapper.readTree(started).at("/data/id").asText();
        String request = """
                {"assessmentId":"%s","answers":[
                  {"questionId":"10000000-0000-0000-0000-000000000001","answerId":"20000000-0000-0000-0000-000000000001"},
                  {"questionId":"10000000-0000-0000-0000-000000000002","answerId":"20000000-0000-0000-0000-000000000003"},
                  {"questionId":"10000000-0000-0000-0000-000000000003","answerId":"20000000-0000-0000-0000-000000000005"},
                  {"questionId":"10000000-0000-0000-0000-000000000004","answerId":"20000000-0000-0000-0000-000000000008"}
                ]}
                """.formatted(assessmentId);
        mvc.perform(post("/api/v1/assessment/submit").with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
    }
}
