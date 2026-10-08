package com.erudit.content.service;

import com.erudit.content.dto.ContentPage;
import com.erudit.content.model.Content;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.model.ContentType;
import com.erudit.content.model.Difficulty;
import com.erudit.content.repository.ContentRepository;

import com.erudit.openapi.model.ContentUpsertRequest;
import com.erudit.web.exception.NotFoundException;
import com.erudit.web.exception.ConflictException;
import com.erudit.web.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import com.erudit.payment.service.PremiumAccessService;
import com.erudit.web.exception.ForbiddenException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ContentService {
    private final ContentRepository repository;
    private final QuizCardService quizCardService;
    private final ObjectProvider<PremiumAccessService> premiumAccess;

    public ContentService(ContentRepository repository, QuizCardService quizCardService,
                          ObjectProvider<PremiumAccessService> premiumAccess) {
        this.repository = repository;
        this.quizCardService = quizCardService;
        this.premiumAccess = premiumAccess;
    }

    @Transactional
    public Content create(ContentUpsertRequest request, String authorId) {
        requireCategory(request.getCategoryId());
        Content content = fromRequest(UUID.randomUUID(), request, authorId,
                ContentStatus.PENDING_MODERATION, Instant.now(), Boolean.TRUE.equals(request.getPremiumLocked()));
        repository.save(content);
        Content saved = find(content.id());
        quizCardService.regenerate(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Content get(UUID id, boolean admin) {
        return get(id, admin, null);
    }

    @Transactional(readOnly = true)
    public Content get(UUID id, boolean admin, String userId) {
        Content content = find(id);
        if (!admin && content.status() != ContentStatus.PUBLISHED) {
            throw new NotFoundException("Content not found");
        }
        if (!admin && content.premiumLocked() && !hasPremiumAccess(userId)) {
            throw new ForbiddenException("Active premium subscription is required");
        }
        return content;
    }

    public boolean hasPremiumAccess(String userId) {
        if (userId == null) return false;
        try {
            PremiumAccessService access = premiumAccess.getIfAvailable();
            return access != null && access.isPremiumActive(UUID.fromString(userId));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Transactional
    public Content update(UUID id, ContentUpsertRequest request) {
        Content current = find(id);
        requireCategory(request.getCategoryId());
        boolean premium = request.getPremiumLocked() == null ? current.premiumLocked() : request.getPremiumLocked();
        Content updated = fromRequest(id, request, current.authorId(), current.status(), current.createdAt(), premium);
        repository.update(updated);
        repository.updateStatus(id, ContentStatus.PENDING_MODERATION);
        Content saved = find(id);
        quizCardService.regenerate(saved);
        return saved;
    }

    @Transactional
    public void archive(UUID id) {
        find(id);
        repository.updateStatus(id, ContentStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public ContentPage moderationQueue(Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? 20 : requestedSize;
        if (page < 0 || size < 1 || size > 100) {
            throw new ValidationException("page must be non-negative and size must be between 1 and 100");
        }
        long total = repository.countByStatus(ContentStatus.PENDING_MODERATION);
        return new ContentPage(repository.findByStatus(ContentStatus.PENDING_MODERATION, page, size),
                total, page, size);
    }

    @Transactional
    public Content moderate(UUID id, ContentStatus decision) {
        if (decision != ContentStatus.PUBLISHED && decision != ContentStatus.REJECTED) {
            throw new IllegalArgumentException("Unsupported moderation decision");
        }
        Content current = find(id);
        if (current.status() != ContentStatus.PENDING_MODERATION) {
            throw new ConflictException("Content has already been moderated");
        }
        repository.updateStatus(id, decision);
        return find(id);
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
                                ContentStatus status, Instant createdAt, boolean premiumLocked) {
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
                request.getMediaUrl(), difficulty, estimatedMinutes, authorId, status, createdAt,
                tags, premiumLocked);
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
