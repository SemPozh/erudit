package com.erudit.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void servesContractAndSwaggerUi() throws Exception {
        mvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/content/{id}/report")))
                .andExpect(content().string(containsString("/hello")))
                .andExpect(content().string(containsString("/api/v1/events/batch")))
                .andExpect(content().string(containsString("/api/v1/analytics/notifications")))
                .andExpect(content().string(containsString("x-implementation-status: planned")));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/openapi.yaml"));
    }
}
