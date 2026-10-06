package com.erudit.content;

import com.erudit.user.service.UserPreferenceProvider;
import com.erudit.web.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ContentFeedService {
    private final ContentCatalogRepository repository;
    private final UserPreferenceProvider preferences;

    public ContentFeedService(ContentCatalogRepository repository, UserPreferenceProvider preferences) {
        this.repository = repository;
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public ContentPage feed(UUID userId, Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? 20 : requestedSize;
        if (page < 0 || size < 1 || size > 50) {
            throw new ValidationException("page must be non-negative and size must be between 1 and 50");
        }
        return repository.personalizedFeed(userId, preferences.preferencesFor(userId).favoriteCategories(), page, size);
    }
}
