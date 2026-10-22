package com.erudit.content.service;

import lombok.RequiredArgsConstructor;
import com.erudit.content.dto.CategoryPageResponse;
import com.erudit.content.dto.ContentCatalogRequest;
import com.erudit.content.dto.ContentPageResponse;
import com.erudit.content.model.ContentType;
import com.erudit.content.model.Difficulty;
import com.erudit.content.repository.ContentCatalogRepository;
import com.erudit.content.repository.ContentRepository;

import com.erudit.web.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentCatalogService {
    private final ContentCatalogRepository repository;
    private final ContentRepository contentRepository;



    @Transactional(readOnly = true)
    public ContentPageResponse find(int page, int size, java.util.UUID categoryId, String type,
                            String difficulty, Boolean premium, String query, String sort) {
        if (page < 0 || size < 1 || size > 50) throw new ValidationException("size must be between 1 and 50");
        return repository.find(new ContentCatalogRequest(page, size, categoryId,
                parseContentType(type), parseDifficulty(difficulty),
                premium, query, parseSort(sort)));
    }

    @Transactional(readOnly = true)
    public CategoryPageResponse categories(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new ValidationException("size must be between 1 and 50");
        return new CategoryPageResponse(contentRepository.findCategories(page, size),
                contentRepository.countCategories(), page, size);
    }

    private static ContentCatalogRequest.Sort parseSort(String value) {
        if (value == null || value.isBlank() || value.equals("createdAt,desc"))
            return ContentCatalogRequest.Sort.CREATED_DESC;
        return switch (value) {
            case "createdAt,asc" -> ContentCatalogRequest.Sort.CREATED_ASC;
            case "title,asc" -> ContentCatalogRequest.Sort.TITLE_ASC;
            case "title,desc" -> ContentCatalogRequest.Sort.TITLE_DESC;
            case "estimatedMinutes,asc" -> ContentCatalogRequest.Sort.DURATION_ASC;
            case "estimatedMinutes,desc" -> ContentCatalogRequest.Sort.DURATION_DESC;
            default -> throw new ValidationException("Unsupported sort");
        };
    }

    private static ContentType parseContentType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ContentType.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Unsupported type");
        }
    }

    private static Difficulty parseDifficulty(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Difficulty.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Unsupported difficulty");
        }
    }
}
