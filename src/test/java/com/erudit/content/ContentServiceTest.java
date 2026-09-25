package com.erudit.content;

import com.erudit.openapi.model.ContentUpsertRequest;
import com.erudit.web.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ContentServiceTest {
    @Autowired private ContentService service;
    @Autowired private ContentRepository repository;
    @Autowired private ContentReportService reportService;
    @Autowired private ContentReportRepository reportRepository;

    @Test
    void preservesIdentityAndStatusWhileUpdatingThenArchives() {
        UUID categoryId = category();
        Content created = service.create(request(categoryId, "Draft", new LinkedHashSet<>(List.of("z", "a"))), "admin-1");
        Content updated = service.update(created.id(), request(categoryId, "Updated", Set.of("new")));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.authorId()).isEqualTo("admin-1");
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(updated.tags()).containsExactly("new");

        ContentReport report = reportService.report(created.id(), "Content needs an editorial review");
        service.archive(created.id());
        Content archived = repository.findById(created.id()).orElseThrow();
        assertThat(archived.status()).isEqualTo(ContentStatus.ARCHIVED);
        assertThat(archived.title()).isEqualTo("Updated");
        assertThat(reportRepository.findById(report.id())).isPresent();
    }

    @Test
    void hidesUnpublishedContentFromPublicAndKeepsItForAdmin() {
        Content draft = service.create(request(category(), "Draft", Set.of()), "admin-1");
        assertThatThrownBy(() -> service.get(draft.id(), false)).isInstanceOf(NotFoundException.class);
        assertThat(service.get(draft.id(), true)).isEqualTo(draft);
    }

    private UUID category() {
        UUID id = UUID.randomUUID();
        repository.saveCategory(new Category(id, "Category " + id));
        return id;
    }

    private static ContentUpsertRequest request(UUID categoryId, String title, Set<String> tags) {
        return new ContentUpsertRequest(categoryId, ContentUpsertRequest.TypeEnum.ARTICLE, title)
                .description("Description").body("Body")
                .difficulty(ContentUpsertRequest.DifficultyEnum.BEGINNER)
                .estimatedMinutes(5).tags(tags);
    }
}
