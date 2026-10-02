package com.erudit.content;

import com.erudit.openapi.model.ContentUpsertRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuizCardIntegrationTest {
    private static final Principal USER = () -> "user-1";

    @Autowired private MockMvc mvc;
    @Autowired private ContentService contentService;
    @Autowired private ContentRepository contentRepository;
    @Autowired private QuizCardRepository cardRepository;

    @Test
    void generatesPersistsAndReturnsCardsForPublishedContent() throws Exception {
        UUID categoryId = UUID.randomUUID();
        contentRepository.saveCategory(new Category(categoryId, "Quiz category " + categoryId));
        Content content = contentService.create(new ContentUpsertRequest(
                        categoryId, ContentUpsertRequest.TypeEnum.ARTICLE, "Космос")
                .body("Земля вращается вокруг Солнца. Марс называют красной планетой.")
                .tags(Set.of()), "author-1");
        contentRepository.updateStatus(content.id(), ContentStatus.PUBLISHED);

        mvc.perform(get("/api/v1/content/{id}/quiz-cards", content.id()).principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fact").value("Земля вращается вокруг Солнца."))
                .andExpect(jsonPath("$.data[0].question").isNotEmpty())
                .andExpect(jsonPath("$.data[0].answers.length()").value(2));

        assertThat(cardRepository.findByContentId(content.id())).first()
                .satisfies(card -> assertThat(card.correctAnswer()).isEqualTo(card.fact()));
    }

    @Test
    void hidesDraftFromUserButAllowsAdminAndRequiresAuthentication() throws Exception {
        UUID categoryId = UUID.randomUUID();
        contentRepository.saveCategory(new Category(categoryId, "Draft category " + categoryId));
        Content draft = contentService.create(new ContentUpsertRequest(
                categoryId, ContentUpsertRequest.TypeEnum.FACT_CARD, "Факт"), "author-1");

        mvc.perform(get("/api/v1/content/{id}/quiz-cards", draft.id()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/content/{id}/quiz-cards", draft.id()).principal(USER))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/content/{id}/quiz-cards", draft.id()).with(request -> {
                    request.setUserPrincipal(() -> "admin-1");
                    request.addUserRole("ADMIN");
                    return request;
                }))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }
}
