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
    private final QuizCardService quizCardService;
    private final ContentCatalogService catalogService;
    private final ContentProgressService progressService;
    private final ContentFeedService feedService;
    private final HttpServletRequest servletRequest;

    public ContentController(ContentService service, QuizCardService quizCardService,
                             ContentCatalogService catalogService, ContentProgressService progressService,
                             ContentFeedService feedService, HttpServletRequest servletRequest) {
        this.service = service;
        this.quizCardService = quizCardService;
        this.catalogService = catalogService;
        this.progressService = progressService;
        this.feedService = feedService;
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
        return ResponseEntity.ok(response(service.get(id, isAdmin(), currentUserOrNull())));
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
        return new ContentItemResponse(item(content));
    }

    static ContentItem item(Content content) {
        return new ContentItem(content.id(), ContentItem.TypeEnum.fromValue(content.type().name()),
                content.title(), content.status().name())
                .categoryId(content.categoryId()).description(content.description()).body(content.body())
                .mediaUrl(content.mediaUrl()).difficulty(content.difficulty().name())
                .estimatedMinutes(content.estimatedMinutes()).authorId(content.authorId())
                .tags(content.tags()).premiumLocked(content.premiumLocked());
    }

    // Routes below belong to later tasks sharing the Content tag.
    @Override
    public ResponseEntity<ContentProgressResponse> completeContent(UUID id) {
        return ResponseEntity.ok(progressResponse(progressService.complete(currentUser(), sessionId(), id)));
    }
    @Override
    public ResponseEntity<ContentListResponse> getContentFeed(Integer page, Integer size) {
        ContentPage result = feedService.feed(currentUserId(), page, size);
        var pagination = new com.erudit.openapi.model.PageMetadata(
                result.page(), result.size(), result.total(), result.totalPages());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new ContentListResponse(result.items().stream().map(ContentController::item).toList())
                        .pagination(pagination));
    }
    @Override
    public ResponseEntity<ContentProgressResponse> getContentProgress(UUID id) {
        return ResponseEntity.ok(progressResponse(progressService.get(currentUser(), id)));
    }
    @Override
    public ResponseEntity<QuizCardListResponse> getContentQuizCards(UUID id) {
        if (servletRequest.getUserPrincipal() == null) {
            throw new com.erudit.web.UnauthorizedException("Authentication is required");
        }
        service.get(id, isAdmin(), currentUser());
        java.util.List<com.erudit.openapi.model.QuizCard> cards = quizCardService.getForContent(id, isAdmin())
                .stream().map(card -> new com.erudit.openapi.model.QuizCard(
                        card.id(), card.fact(), card.question(), card.answers())).toList();
        return ResponseEntity.ok(new QuizCardListResponse(cards));
    }
    @Override
    public ResponseEntity<CategoryListResponse> listCategories(Integer page, Integer size) {
        CategoryPage result = catalogService.categories(page, size);
        var data = result.items().stream().map(category ->
                new com.erudit.openapi.model.Category(category.id(), category.name())).toList();
        var pagination = new com.erudit.openapi.model.PageMetadata(
                result.page(), result.size(), result.total(), result.totalPages());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new CategoryListResponse(data).pagination(pagination));
    }

    @Override
    public ResponseEntity<ContentListResponse> listContent(Integer page, Integer size, @Nullable UUID categoryId,
            @Nullable String type, @Nullable String difficulty, @Nullable Boolean premium,
            @Nullable String query, @Nullable String sort) {
        ContentPage result = catalogService.find(page, size, categoryId, type, difficulty, premium, query, sort);
        var pagination = new com.erudit.openapi.model.PageMetadata(
                result.page(), result.size(), result.total(), result.totalPages());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new ContentListResponse(result.items().stream().map(ContentController::item).toList())
                        .pagination(pagination));
    }

    @Override
    public ResponseEntity<FormatList> listFormats() {
        return ResponseEntity.ok(new FormatList(java.util.Arrays.stream(ContentType.values())
                .map(Enum::name).toList()));
    }
    @Override
    public ResponseEntity<ContentProgressResponse> markContentViewed(UUID id) {
        return ResponseEntity.ok(progressResponse(progressService.markViewed(currentUser(), sessionId(), id)));
    }

    private String currentUser() {
        if (servletRequest.getUserPrincipal() == null) {
            throw new com.erudit.web.UnauthorizedException("Authentication is required");
        }
        return servletRequest.getUserPrincipal().getName();
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(currentUser());
        } catch (IllegalArgumentException exception) {
            throw new com.erudit.web.UnauthorizedException("Authenticated user id is invalid");
        }
    }

    private String currentUserOrNull() {
        return servletRequest.getUserPrincipal() == null ? null : servletRequest.getUserPrincipal().getName();
    }

    private String sessionId() {
        String header = servletRequest.getHeader("X-Session-Id");
        return header == null || header.isBlank() ? "api:" + currentUser() : header;
    }

    private static ContentProgressResponse progressResponse(ContentProgress progress) {
        var data = new com.erudit.openapi.model.ContentProgress(progress.contentId(), progress.status().name())
                .viewedAt(progress.viewedAt().atOffset(java.time.ZoneOffset.UTC));
        if (progress.completedAt() != null) {
            data.completedAt(progress.completedAt().atOffset(java.time.ZoneOffset.UTC));
        }
        return new ContentProgressResponse(data);
    }
}
