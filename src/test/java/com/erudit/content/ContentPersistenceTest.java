package com.erudit.content;

import com.erudit.web.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ContentPersistenceTest {
    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentReportRepository reportRepository;

    @Autowired
    private ContentReportService reportService;

    @Test
    void savesContentWithMetadataAndReports() {
        UUID categoryId = UUID.randomUUID();
        contentRepository.saveCategory(new Category(categoryId, "Science " + categoryId));
        UUID contentId = UUID.randomUUID();
        Content content = new Content(contentId, categoryId, ContentType.ARTICLE,
                "Test article", "Short description", "Body", null, Difficulty.BEGINNER,
                5, "author-1", ContentStatus.DRAFT, Instant.now(), List.of("history", "science"));
        contentRepository.save(content);

        assertThat(contentRepository.findCategory(categoryId)).contains(new Category(categoryId, "Science " + categoryId));
        Content saved = contentRepository.findById(contentId).orElseThrow();
        assertThat(saved.type()).isEqualTo(ContentType.ARTICLE);
        assertThat(saved.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(saved.tags()).containsExactly("history", "science");
        assertThat(saved.estimatedMinutes()).isEqualTo(5);

        ContentReport report = reportService.report(contentId, "The article contains a factual error");
        ContentReport savedReport = reportRepository.findById(report.id()).orElseThrow();
        assertThat(savedReport.contentId()).isEqualTo(contentId);
        assertThat(savedReport.reason()).isEqualTo(report.reason());
    }

    @Test
    void rejectsReportForMissingContent() {
        assertThatThrownBy(() -> reportService.report(UUID.randomUUID(), "Missing content"))
                .isInstanceOf(NotFoundException.class);
    }
}
