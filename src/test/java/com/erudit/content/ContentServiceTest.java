package com.erudit.content;

import com.erudit.content.model.Category;
import com.erudit.content.model.Content;
import com.erudit.content.model.ContentReport;
import com.erudit.content.model.ContentReportStatus;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.model.ReportDecision;
import com.erudit.content.repository.ContentModerationAuditRepository;
import com.erudit.content.repository.ContentReportRepository;
import com.erudit.content.repository.ContentRepository;
import com.erudit.content.service.ContentReportService;
import com.erudit.content.service.ContentService;

import com.erudit.openapi.model.ContentUpsertRequest;
import com.erudit.web.exception.NotFoundException;
import com.erudit.web.exception.ConflictException;
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
    @Autowired private ContentModerationAuditRepository auditRepository;

    @Test
    void preservesIdentityAndStatusWhileUpdatingThenArchives() {
        UUID categoryId = category();
        Content created = service.create(request(categoryId, "Draft", new LinkedHashSet<>(List.of("z", "a"))), "admin-1");
        Content updated = service.update(created.id(), request(categoryId, "Updated", Set.of("new")));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.authorId()).isEqualTo("admin-1");
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.status()).isEqualTo(ContentStatus.PENDING_MODERATION);
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

    @Test
    void queuesAndModeratesContentExactlyOnce() {
        Content pending = service.create(request(category(), "Pending", Set.of()), "admin-1");

        assertThat(pending.status()).isEqualTo(ContentStatus.PENDING_MODERATION);
        assertThat(service.moderationQueue(0, 20).items()).extracting(Content::id).contains(pending.id());
        assertThat(service.moderate(pending.id(), ContentStatus.PUBLISHED).status())
                .isEqualTo(ContentStatus.PUBLISHED);
        assertThatThrownBy(() -> service.moderate(pending.id(), ContentStatus.REJECTED))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resolvesReportsAndArchivesContentOnlyWhenAccepted() {
        Content acceptedContent = service.create(request(category(), "Accepted report", Set.of()), "admin-1");
        service.moderate(acceptedContent.id(), ContentStatus.PUBLISHED);
        ContentReport accepted = reportService.report(acceptedContent.id(), "Incorrect fact");

        reportService.resolve(accepted.id(), ReportDecision.ACCEPT, " Confirmed ", "moderator-1");

        ContentReport acceptedResult = reportRepository.findById(accepted.id()).orElseThrow();
        assertThat(acceptedResult.status()).isEqualTo(ContentReportStatus.RESOLVED);
        assertThat(acceptedResult.decision()).isEqualTo(ReportDecision.ACCEPT);
        assertThat(acceptedResult.resolutionComment()).isEqualTo("Confirmed");
        assertThat(acceptedResult.resolvedBy()).isEqualTo("moderator-1");
        assertThat(repository.findById(acceptedContent.id()).orElseThrow().status())
                .isEqualTo(ContentStatus.ARCHIVED);
        assertThat(auditRepository.countReportResolutions(accepted.id())).isEqualTo(1);
        assertThatThrownBy(() -> reportService.resolve(
                accepted.id(), ReportDecision.REJECT, null, "moderator-2"))
                .isInstanceOf(ConflictException.class);

        Content rejectedContent = service.create(request(category(), "Rejected report", Set.of()), "admin-1");
        service.moderate(rejectedContent.id(), ContentStatus.PUBLISHED);
        ContentReport rejected = reportService.report(rejectedContent.id(), "Not a violation");
        reportService.resolve(rejected.id(), ReportDecision.REJECT, null, "moderator-1");
        assertThat(repository.findById(rejectedContent.id()).orElseThrow().status())
                .isEqualTo(ContentStatus.PUBLISHED);
        assertThat(reportService.openReports(0, 50).items())
                .extracting(ContentReport::id).doesNotContain(accepted.id(), rejected.id());
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
