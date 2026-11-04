package com.erudit.content.service;

import com.erudit.content.model.ContentItem;
import com.erudit.content.repository.CategoryRepository;
import com.erudit.content.repository.ContentItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentItemService {
    private static final Set<String> TYPES = Set.of("ARTICLE", "FACT_CARD", "AUDIO", "VIDEO", "COMBINED", "QUIZ_BLOCK");
    private static final Set<String> DIFFICULTIES = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final Map<String, String> SORTS = Map.of(
            "createdAt,desc", "created_at DESC, id", "createdAt,asc", "created_at ASC, id",
            "title,asc", "title ASC, id", "title,desc", "title DESC, id",
            "estimatedMinutes,asc", "estimated_minutes ASC, id",
            "estimatedMinutes,desc", "estimated_minutes DESC, id");
    private final ContentItemRepository repository;
    private final CategoryRepository categories;

    @Transactional
    public ContentItem create(Draft draft) {
        validate(draft);
        ContentItem item = new ContentItem(UUID.randomUUID(), draft.categoryId(), draft.type(), draft.title().trim(),
                draft.description().trim(), draft.body().trim(), draft.mediaUrl(), draft.difficulty(),
                draft.estimatedMinutes(), draft.premiumLocked(), "PUBLISHED", cleanTags(draft.tags()), Instant.now());
        repository.save(item);
        return item;
    }

    @Transactional(readOnly = true)
    public ContentItem get(UUID id) {
        return repository.find(id).orElseThrow(() -> new CategoryNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page list(int page, int size, UUID categoryId, String type, String difficulty, Boolean premium,
                     String query, String sort) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("Invalid page or size");
        String order = SORTS.get(sort == null ? "createdAt,desc" : sort);
        if (order == null) throw new IllegalArgumentException("Unsupported sort");
        if (type != null && !TYPES.contains(type)) throw new IllegalArgumentException("Unsupported type");
        if (difficulty != null && !DIFFICULTIES.contains(difficulty)) throw new IllegalArgumentException("Unsupported difficulty");
        return new Page(repository.list(page, size, categoryId, type, difficulty, premium, query, order),
                repository.count(categoryId, type, difficulty, premium, query), page, size);
    }

    @Transactional
    public ContentItem update(UUID id, Draft draft) {
        validate(draft);
        ContentItem old = get(id);
        ContentItem item = new ContentItem(id, draft.categoryId(), draft.type(), draft.title().trim(),
                draft.description().trim(), draft.body().trim(), draft.mediaUrl(), draft.difficulty(),
                draft.estimatedMinutes(), draft.premiumLocked(), old.status(), cleanTags(draft.tags()), old.createdAt());
        repository.update(item);
        return item;
    }

    @Transactional
    public void archive(UUID id) {
        if (!repository.archive(id)) throw new CategoryNotFoundException(id);
    }

    public List<String> formats() { return TYPES.stream().sorted().toList(); }

    private void validate(Draft draft) {
        if (!categories.findById(draft.categoryId()).isPresent()) throw new IllegalArgumentException("Unknown categoryId");
        if (!TYPES.contains(draft.type()) || !DIFFICULTIES.contains(draft.difficulty())) throw new IllegalArgumentException("Unsupported type or difficulty");
        if (draft.title() == null || draft.title().isBlank() || draft.description() == null || draft.description().isBlank()
                || draft.body() == null || draft.body().isBlank() || draft.estimatedMinutes() < 1 || draft.estimatedMinutes() > 1440)
            throw new IllegalArgumentException("Invalid content fields");
    }

    private List<String> cleanTags(List<String> tags) {
        if (tags == null) return List.of();
        if (tags.size() > 20) throw new IllegalArgumentException("At most 20 tags are allowed");
        return tags.stream().map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
    }

    public record Draft(UUID categoryId, String type, String title, String description, String body, String mediaUrl,
                        String difficulty, int estimatedMinutes, boolean premiumLocked, List<String> tags) {}
    public record Page(List<ContentItem> items, long total, int page, int size) {}
}
