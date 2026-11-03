package com.erudit.content.service;

import com.erudit.content.model.Category;
import com.erudit.content.repository.CategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository repository;

    @Transactional
    public Category create(String name) {
        try {
            return repository.create(new Category(UUID.randomUUID(), normalize(name)));
        } catch (DataIntegrityViolationException exception) {
            throw new CategoryConflictException("Category name is already in use", exception);
        }
    }

    @Transactional(readOnly = true)
    public Category get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public CategoryPage list(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 50");
        }
        return new CategoryPage(repository.findAll(page, size), repository.count(), page, size);
    }

    @Transactional
    public Category update(UUID id, String name) {
        try {
            return repository.update(id, normalize(name)).orElseThrow(() -> new CategoryNotFoundException(id));
        } catch (DataIntegrityViolationException exception) {
            throw new CategoryConflictException("Category name is already in use", exception);
        }
    }

    @Transactional
    public void delete(UUID id) {
        try {
            if (!repository.delete(id)) {
                throw new CategoryNotFoundException(id);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new CategoryConflictException("Category is used by content and cannot be deleted", exception);
        }
    }

    private String normalize(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException("name must contain from 1 to 100 characters");
        }
        return normalized;
    }

    public record CategoryPage(List<Category> items, long total, int page, int size) {
    }
}
