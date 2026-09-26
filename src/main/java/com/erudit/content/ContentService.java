package com.erudit.content;

import com.erudit.openapi.model.ContentUpsertRequest;
import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ContentService {
    private final ContentRepository repository;

    public ContentService(ContentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Content create(ContentUpsertRequest request, String authorId) {
        requireCategory(request.getCategoryId());
        Content content = fromRequest(UUID.randomUUID(), request, authorId,
                ContentStatus.DRAFT, Instant.now());
        repository.save(content);
        return find(content.id());
    }

    @Transactional(readOnly = true)
    public Content get(UUID id, boolean admin) {
        Content content = find(id);
        if (!admin && content.status() != ContentStatus.PUBLISHED) {
            throw new NotFoundException("Content not found");
        }
        return content;
    }

    @Transactional
    public Content update(UUID id, ContentUpsertRequest request) {
        Content current = find(id);
        requireCategory(request.getCategoryId());
        Content updated = fromRequest(id, request, current.authorId(), current.status(), current.createdAt());
        repository.update(updated);
        return find(id);
    }

    @Transactional
    public void archive(UUID id) {
        find(id);
        repository.updateStatus(id, ContentStatus.ARCHIVED);
    }

    private Content find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Content not found"));
    }

    private void requireCategory(UUID categoryId) {
        if (repository.findCategory(categoryId).isEmpty()) {
            throw new ValidationException("Unknown categoryId");
        }
    }

    private Content fromRequest(UUID id, ContentUpsertRequest request, String authorId,
                                ContentStatus status, Instant createdAt) {
        if (request.getTitle().isBlank()) {
            throw new ValidationException("title must not be blank");
        }
        ContentType type = parseEnum(ContentType.class, request.getType().getValue(), "type");
        Difficulty difficulty = request.getDifficulty() == null
                ? Difficulty.BEGINNER
                : parseEnum(Difficulty.class, request.getDifficulty().getValue(), "difficulty");
        int estimatedMinutes = request.getEstimatedMinutes() == null ? 1 : request.getEstimatedMinutes();
        if (estimatedMinutes < 1) {
            throw new ValidationException("estimatedMinutes must be positive");
        }
        List<String> tags = request.getTags() == null ? List.of() : request.getTags().stream()
                .map(String::trim).filter(tag -> !tag.isEmpty()).distinct().sorted().toList();
        return new Content(id, request.getCategoryId(), type, request.getTitle().trim(),
                valueOrEmpty(request.getDescription()), valueOrEmpty(request.getBody()),
                request.getMediaUrl(), difficulty, estimatedMinutes, authorId, status, createdAt, tags);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Unsupported " + field + ": " + value);
        }
    }
}
