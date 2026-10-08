package com.erudit.content;

import com.erudit.content.model.Category;
import com.erudit.content.model.Content;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.repository.ContentRepository;
import com.erudit.content.service.ContentService;

import com.erudit.openapi.model.ContentUpsertRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentCatalogIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ContentService contentService;
    @Autowired private ContentRepository contentRepository;

    @Test
    void combinesFiltersSearchSortingAndPagination() throws Exception {
        UUID science = category("Science");
        UUID history = category("History");
        publish(content(science, "Premium Java", "Spring platform", "ARTICLE", "ADVANCED", true, 15));
        publish(content(science, "Java basics", "Language", "ARTICLE", "BEGINNER", false, 5));
        publish(content(history, "Premium history", "Archive", "VIDEO", "ADVANCED", true, 10));

        mvc.perform(get("/api/v1/content")
                        .param("categoryId", science.toString()).param("type", "ARTICLE")
                        .param("difficulty", "ADVANCED").param("premium", "true")
                        .param("query", "spring").param("sort", "title,asc")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].title").value("Premium Java"))
                .andExpect(jsonPath("$.data[0].premiumLocked").value(true))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    void returnsPagedReferenceDataAndRejectsUnsafeSortAndLargePage() throws Exception {
        category("Reference A");
        category("Reference B");
        mvc.perform(get("/api/v1/content/categories").param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/content/formats"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/api/v1/content").param("sort", "title; DROP TABLE content"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/content").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    private UUID category(String prefix) {
        UUID id = UUID.randomUUID();
        contentRepository.saveCategory(new Category(id, prefix + " " + id));
        return id;
    }

    private Content content(UUID categoryId, String title, String description, String type,
                            String difficulty, boolean premium, int minutes) {
        return contentService.create(new ContentUpsertRequest(categoryId,
                        ContentUpsertRequest.TypeEnum.fromValue(type), title)
                .description(description).body(description + ".")
                .difficulty(ContentUpsertRequest.DifficultyEnum.fromValue(difficulty))
                .estimatedMinutes(minutes).premiumLocked(premium).tags(Set.of("catalog")), "admin");
    }

    private void publish(Content content) {
        contentRepository.updateStatus(content.id(), ContentStatus.PUBLISHED);
    }
}
