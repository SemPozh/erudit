package com.erudit.content;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.config.name=isolated-test"
})
class CategoryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @Test
    void supportsCrudPaginationAndValidation() throws Exception {
        String body = mvc.perform(post("/api/v1/content/categories")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Astronomy \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.name").value("Astronomy"))
                .andReturn().getResponse().getContentAsString();
        String id = body.substring(body.indexOf("\"id\":\"") + 6, body.indexOf("\",\"name\""));

        mvc.perform(get("/api/v1/content/categories").param("page", "0").param("size", "2"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$.data", hasSize(2)));
        mvc.perform(put("/api/v1/content/categories/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Space\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Space"));
        mvc.perform(delete("/api/v1/content/categories/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/content/categories/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/content/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/content/categories").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConflictForDuplicateNameAndForeignKeyUsage() throws Exception {
        mvc.perform(post("/api/v1/content/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Наука\"}"))
                .andExpect(status().isConflict());

        UUID id = UUID.fromString("10000000-0000-0000-0000-000000000001");
        jdbc.execute("CREATE TABLE IF NOT EXISTS category_test_usage (id UUID PRIMARY KEY, category_id UUID NOT NULL REFERENCES categories(id))");
        jdbc.update("INSERT INTO category_test_usage (id, category_id) VALUES (?, ?) ON CONFLICT DO NOTHING", UUID.randomUUID(), id);
        mvc.perform(delete("/api/v1/content/categories/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category is used by content and cannot be deleted"));
    }

    @Test
    void exposesDeterministicDemoCategories() throws Exception {
        mvc.perform(get("/api/v1/content/categories/{id}", "10000000-0000-0000-0000-000000000002"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("История"));
    }
}
