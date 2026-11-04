package com.erudit.content;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.config.name=isolated-test",
        "media.max-size-bytes=16",
        "media.storage-path=build/test-media"
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

    @Test
    void contentCrudCatalogFiltersAndArchiveWork() throws Exception {
        String request = """
                {"categoryId":"10000000-0000-0000-0000-000000000001","type":"ARTICLE",
                 "title":"Blue sky","description":"Light scattering","body":"Detailed explanation",
                 "difficulty":"BEGINNER","estimatedMinutes":5,"premiumLocked":false,"tags":["science","light"]}
                """;
        String body = mvc.perform(post("/api/v1/content").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING_MODERATION"))
                .andReturn().getResponse().getContentAsString();
        String id = body.substring(body.indexOf("\"id\":\"") + 6, body.indexOf("\",\"status\""));

        mvc.perform(post("/internal/v1/moderation").contentType(MediaType.APPLICATION_JSON).content(String.format("{\"contentId\":\"%s\",\"status\":\"PUBLISHED\"}",id))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/content").param("categoryId", "10000000-0000-0000-0000-000000000001")
                        .param("query", "blue").param("sort", "title,asc"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].id").value(id));
        mvc.perform(get("/api/v1/content").param("sort", "title;drop table content"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/content/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("Blue sky", "Red sunset")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Red sunset"));
        mvc.perform(delete("/api/v1/content/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/content").param("query", "sunset"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "0"));
        mvc.perform(get("/api/v1/content/formats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(6)));
    }

    @Test
    void mediaUploadDownloadAndLimitsWork() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile file = new MockMultipartFile("file", "demo.png", "image/png", png);
        String body = mvc.perform(multipart("/api/v1/media").file(file)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = body.substring(body.indexOf("\"id\":\"") + 6, body.indexOf("\",\"url\""));
        mvc.perform(get("/api/v1/media/{id}", id)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "bad.png", "image/png", new byte[17])))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "fake.png", "image/png", "not png".getBytes())))
                .andExpect(status().isBadRequest());
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.erudit.integration.InternalClients.EntitlementClient entitlements;

    @Autowired com.erudit.content.service.ContentItemService content;

    @Test void premiumContentUsesRemoteEntitlementAndRedactsOnOutage() throws Exception {
        UUID user = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var item = content.create(new com.erudit.content.service.ContentItemService.Draft(
                UUID.fromString("10000000-0000-0000-0000-000000000001"), "ARTICLE", "Premium test article",
                "Secret description", "Secret body", "https://example.com/premium.mp4", "BEGINNER", 5, true, java.util.List.of()));
        mvc.perform(post("/internal/v1/moderation").contentType(MediaType.APPLICATION_JSON).content(String.format("{\"contentId\":\"%s\",\"status\":\"PUBLISHED\"}",item.id()))).andExpect(status().isOk());
        org.mockito.Mockito.when(entitlements.get(user)).thenReturn(
                new com.erudit.integration.InternalContracts.Entitlement(user,true,null));
        mvc.perform(get("/api/v1/content/{id}",item.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("Secret body"));
        org.mockito.Mockito.when(entitlements.get(user)).thenThrow(new IllegalStateException("offline"));
        mvc.perform(get("/api/v1/content/{id}",item.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("Premium content"))
                .andExpect(jsonPath("$.data.mediaUrl").isEmpty());
        mvc.perform(get("/api/v1/content").param("query","Premium test article")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].description").value("Premium content"));
    }
}
