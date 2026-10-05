package com.erudit.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssessmentIntegrationTest {
    @Autowired private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private JdbcTemplate jdbc;

    @Test
    void startsAllTopicsAcceptsAnswersAndPersistsRawResult() throws Exception {
        String started = mvc.perform(post("/api/v1/assessment/start").with(user("student-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.questions.length()").value(4))
                .andExpect(jsonPath("$.data.questions[0].answers[0].id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(started).get("data");
        assertThat(data.get("questions").findValuesAsText("topic"))
                .containsExactlyInAnyOrder("SCIENCE", "HISTORY", "TECHNOLOGY", "CULTURE");
        String assessmentId = data.get("id").asText();

        String request = """
                {"assessmentId":"%s","answers":[
                  {"questionId":"10000000-0000-0000-0000-000000000001","answerId":"20000000-0000-0000-0000-000000000001"},
                  {"questionId":"10000000-0000-0000-0000-000000000002","answerId":"20000000-0000-0000-0000-000000000003"},
                  {"questionId":"10000000-0000-0000-0000-000000000003","answerId":"20000000-0000-0000-0000-000000000005"},
                  {"questionId":"10000000-0000-0000-0000-000000000004","answerId":"20000000-0000-0000-0000-000000000008"}
                ]}
                """.formatted(assessmentId);
        String submitted = mvc.perform(post("/api/v1/assessment/submit").with(user("student-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctAnswers").value(3))
                .andExpect(jsonPath("$.data.totalAnswers").value(4))
                .andExpect(jsonPath("$.data.erScore").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID resultId = UUID.fromString(objectMapper.readTree(submitted).at("/data/id").asText());
        assertThat(jdbc.queryForObject("SELECT correct_answers FROM assessment_results WHERE id = ?",
                Integer.class, resultId)).isEqualTo(3);

        mvc.perform(post("/api/v1/assessment/submit").with(user("student-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void rejectsIncompleteAndUnauthenticatedSubmission() throws Exception {
        mvc.perform(post("/api/v1/assessment/start"))
                .andExpect(status().isUnauthorized());
        String started = mvc.perform(post("/api/v1/assessment/start").with(user("student-2")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String assessmentId = objectMapper.readTree(started).at("/data/id").asText();
        String incomplete = """
                {"assessmentId":"%s","answers":[
                  {"questionId":"10000000-0000-0000-0000-000000000001","answerId":"20000000-0000-0000-0000-000000000001"}
                ]}
                """.formatted(assessmentId);
        mvc.perform(post("/api/v1/assessment/submit").with(user("student-2"))
                        .contentType(MediaType.APPLICATION_JSON).content(incomplete))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(
                        "Exactly one answer is required for every assessment question"));
    }
}
