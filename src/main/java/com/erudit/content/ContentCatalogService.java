package com.erudit.content;

import com.erudit.web.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class ContentCatalogService {
    private final ContentCatalogRepository repository;
    private final ContentRepository contentRepository;

    public ContentCatalogService(ContentCatalogRepository repository, ContentRepository contentRepository) {
        this.repository = repository;
        this.contentRepository = contentRepository;
    }

    @Transactional(readOnly = true)
    public ContentPage find(int page, int size, java.util.UUID categoryId, String type,
                            String difficulty, Boolean premium, String query, String sort) {
        if (page < 0 || size < 1 || size > 50) throw new ValidationException("size must be between 1 and 50");
        return repository.find(new ContentCatalogQuery(page, size, categoryId,
                parse(ContentType.class, type, "type"), parse(Difficulty.class, difficulty, "difficulty"),
                premium, query, parseSort(sort)));
    }

    @Transactional(readOnly = true)
    public CategoryPage categories(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new ValidationException("size must be between 1 and 50");
        return new CategoryPage(contentRepository.findCategories(page, size),
                contentRepository.countCategories(), page, size);
    }

    private static ContentCatalogQuery.Sort parseSort(String value) {
        if (value == null || value.isBlank() || value.equals("createdAt,desc")) return ContentCatalogQuery.Sort.CREATED_DESC;
        return switch (value) {
            case "createdAt,asc" -> ContentCatalogQuery.Sort.CREATED_ASC;
            case "title,asc" -> ContentCatalogQuery.Sort.TITLE_ASC;
            case "title,desc" -> ContentCatalogQuery.Sort.TITLE_DESC;
            case "estimatedMinutes,asc" -> ContentCatalogQuery.Sort.DURATION_ASC;
            case "estimatedMinutes,desc" -> ContentCatalogQuery.Sort.DURATION_DESC;
            default -> throw new ValidationException("Unsupported sort");
        };
    }

    private static <T extends Enum<T>> T parse(Class<T> type, String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new ValidationException("Unsupported " + field); }
    }
}
