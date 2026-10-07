package com.erudit.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ContentFeedIntegrationTest {
    @Autowired private ContentRepository contentRepository;
    @Autowired private ContentCatalogRepository catalogRepository;
    @Autowired private ContentProgressRepository progressRepository;

    @Test
    void ranksFavoritesThenBehaviorAndDeprioritizesCompletedContent() {
        UUID favorite = category("Feed favorite");
        UUID behavioral = category("Feed behavioral");
        UUID other = category("Feed other");
        Content favoriteItem = content(favorite, "Favorite", Instant.parse("2030-01-01T00:00:00Z"));
        Content behavioralSignal = content(behavioral, "Viewed signal", Instant.parse("2030-01-02T00:00:00Z"));
        Content behavioralCandidate = content(behavioral, "Behavior candidate", Instant.parse("2030-01-03T00:00:00Z"));
        Content unrelated = content(other, "Unrelated", Instant.parse("2030-01-04T00:00:00Z"));
        String user = UUID.randomUUID().toString();
        progressRepository.insert(new ContentProgress(user, behavioralSignal.id(), ContentProgressStatus.COMPLETED,
                Instant.parse("2030-01-05T00:00:00Z"), Instant.parse("2030-01-05T00:01:00Z")));

        List<UUID> ids = catalogRepository.personalizedFeed(UUID.fromString(user), Set.of(favorite), 0, 50)
                .items().stream().map(Content::id).toList();

        assertThat(ids.indexOf(favoriteItem.id())).isLessThan(ids.indexOf(behavioralCandidate.id()));
        assertThat(ids.indexOf(behavioralCandidate.id())).isLessThan(ids.indexOf(unrelated.id()));
        assertThat(ids.indexOf(behavioralSignal.id())).isGreaterThan(ids.indexOf(unrelated.id()));
    }

    @Test
    void fallsBackToNewestPublishedContentWithoutSignals() {
        UUID category = category("Feed fallback");
        Content older = content(category, "Older", Instant.parse("2035-01-01T00:00:00Z"));
        Content newer = content(category, "Newer", Instant.parse("2035-01-02T00:00:00Z"));

        List<UUID> ids = catalogRepository.personalizedFeed(UUID.randomUUID(), Set.of(), 0, 50)
                .items().stream().map(Content::id).toList();

        assertThat(ids.indexOf(newer.id())).isLessThan(ids.indexOf(older.id()));
    }

    private UUID category(String name) {
        UUID id = UUID.randomUUID();
        contentRepository.saveCategory(new Category(id, name + " " + id));
        return id;
    }

    private Content content(UUID categoryId, String title, Instant createdAt) {
        Content content = new Content(UUID.randomUUID(), categoryId, ContentType.ARTICLE, title,
                "Description", "Body", null, Difficulty.BEGINNER, 5, "author",
                ContentStatus.PUBLISHED, createdAt, List.of("feed"), false);
        contentRepository.save(content);
        return content;
    }
}
