package com.erudit.content;

import com.erudit.events.AnalyticsEventPublisher;
import com.erudit.events.EventPublication;
import com.erudit.events.EventType;
import com.erudit.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ContentProgressService {
    private static final Logger log = LoggerFactory.getLogger(ContentProgressService.class);
    private final ContentProgressRepository repository;
    private final ContentService contentService;
    private final ObjectProvider<AnalyticsEventPublisher> publisherProvider;

    public ContentProgressService(ContentProgressRepository repository, ContentService contentService,
                                  ObjectProvider<AnalyticsEventPublisher> publisherProvider) {
        this.repository = repository;
        this.contentService = contentService;
        this.publisherProvider = publisherProvider;
    }

    @Transactional(readOnly = true)
    public ContentProgress get(String userId, UUID contentId) {
        contentService.get(contentId, false);
        return repository.find(userId, contentId)
                .orElseThrow(() -> new NotFoundException("Content progress not found"));
    }

    @Transactional
    public ContentProgress markViewed(String userId, String sessionId, UUID contentId) {
        contentService.get(contentId, false);
        var existing = repository.find(userId, contentId);
        List<EventType> events = new ArrayList<>();
        events.add(EventType.CONTENT_VIEWED);
        ContentProgress progress;
        if (existing.isEmpty()) {
            progress = new ContentProgress(userId, contentId, ContentProgressStatus.VIEWED,
                    Instant.now(), null);
            repository.insert(progress);
            events.add(EventType.CONTENT_STARTED);
        } else {
            progress = existing.get();
        }
        publishAfterCommit(events, userId, sessionId, contentId);
        return progress;
    }

    @Transactional
    public ContentProgress complete(String userId, String sessionId, UUID contentId) {
        contentService.get(contentId, false);
        Instant now = Instant.now();
        var existing = repository.find(userId, contentId);
        ContentProgress progress;
        List<EventType> events = new ArrayList<>();
        if (existing.isEmpty()) {
            progress = new ContentProgress(userId, contentId, ContentProgressStatus.COMPLETED, now, now);
            repository.insert(progress);
            events.add(EventType.CONTENT_STARTED);
            events.add(EventType.CONTENT_COMPLETED);
        } else if (existing.get().status() == ContentProgressStatus.VIEWED) {
            repository.complete(userId, contentId, now);
            progress = new ContentProgress(userId, contentId, ContentProgressStatus.COMPLETED,
                    existing.get().viewedAt(), now);
            events.add(EventType.CONTENT_COMPLETED);
        } else {
            progress = existing.get();
        }
        publishAfterCommit(events, userId, sessionId, contentId);
        return progress;
    }

    private void publishAfterCommit(List<EventType> types, String userId, String sessionId, UUID contentId) {
        if (types.isEmpty()) return;
        Runnable action = () -> types.forEach(type -> publish(type, userId, sessionId, contentId));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    private void publish(EventType type, String userId, String sessionId, UUID contentId) {
        AnalyticsEventPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) return;
        var result = publisher.publish(new EventPublication(type, userId, sessionId,
                "{\"contentId\":\"" + contentId + "\"}"));
        if (result != null) {
            result.exceptionally(exception -> {
                log.error("Failed to publish {} for content {}", type.value(), contentId, exception);
                return null;
            });
        }
    }
}
