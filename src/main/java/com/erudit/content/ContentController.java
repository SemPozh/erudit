package com.erudit.content;

import com.erudit.openapi.api.ContentApi;
import com.erudit.openapi.model.*;
import com.erudit.web.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ContentController implements ContentApi {
    private final ContentService service;
    private final HttpServletRequest servletRequest;

    public ContentController(ContentService service, HttpServletRequest servletRequest) {
        this.service = service;
        this.servletRequest = servletRequest;
    }

    @Override
    public ResponseEntity<ContentItemResponse> createContent(ContentUpsertRequest request) {
        requireAdmin();
        String authorId = servletRequest.getUserPrincipal() == null
                ? "admin" : servletRequest.getUserPrincipal().getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(response(service.create(request, authorId)));
    }

    @Override
    public ResponseEntity<ContentItemResponse> getContent(UUID id) {
        return ResponseEntity.ok(response(service.get(id, isAdmin())));
    }

    @Override
    public ResponseEntity<ContentItemResponse> updateContent(UUID id, ContentUpsertRequest request) {
        requireAdmin();
        return ResponseEntity.ok(response(service.update(id, request)));
    }

    @Override
    public ResponseEntity<Void> archiveContent(UUID id) {
        requireAdmin();
        service.archive(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin() {
        return servletRequest.isUserInRole("ADMIN");
    }

    private void requireAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenException("ADMIN role is required");
        }
    }

    private static ContentItemResponse response(Content content) {
        ContentItem item = new ContentItem(content.id(), ContentItem.TypeEnum.fromValue(content.type().name()),
                content.title(), content.status().name())
                .categoryId(content.categoryId()).description(content.description()).body(content.body())
                .mediaUrl(content.mediaUrl()).difficulty(content.difficulty().name())
                .estimatedMinutes(content.estimatedMinutes()).authorId(content.authorId())
                .tags(content.tags()).premiumLocked(false);
        return new ContentItemResponse(item);
    }

    // Routes below belong to later tasks sharing the Content tag.
    @Override public ResponseEntity<ContentProgressResponse> completeContent(UUID id) { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<ContentListResponse> getContentFeed(Integer page, Integer size) { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<ContentProgressResponse> getContentProgress(UUID id) { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<QuizCardListResponse> getContentQuizCards(UUID id) { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<CategoryListResponse> listCategories() { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<ContentListResponse> listContent(Integer page, Integer size, @Nullable UUID categoryId,
            @Nullable String type, @Nullable String difficulty, @Nullable Boolean premium,
            @Nullable String query, @Nullable String sort) { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<FormatList> listFormats() { return ResponseEntity.notFound().build(); }
    @Override public ResponseEntity<ContentProgressResponse> markContentViewed(UUID id) { return ResponseEntity.notFound().build(); }
}
