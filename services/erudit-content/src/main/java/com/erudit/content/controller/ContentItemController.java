package com.erudit.content.controller;

import com.erudit.content.model.ContentItem;
import com.erudit.content.openapi.api.ContentApi;
import com.erudit.content.openapi.model.ContentDataResponse;
import com.erudit.content.openapi.model.ContentListResponse;
import com.erudit.content.openapi.model.ContentRequest;
import com.erudit.content.openapi.model.ContentResponse;
import com.erudit.content.openapi.model.FormatListResponse;
import com.erudit.content.openapi.model.PageMetadataResponse;
import com.erudit.content.service.ContentItemService;
import com.erudit.content.service.PremiumAccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ContentItemController implements ContentApi {
    private final ContentItemService service;
    private final PremiumAccessService premium;
    private final HttpServletRequest request;

    @Override
    public ResponseEntity<ContentResponse> createContent(ContentRequest request) {
        ContentItem item = service.create(draft(request));
        return ResponseEntity.created(URI.create("/api/v1/content/" + item.id())).body(response(item));
    }

    @Override public ResponseEntity<ContentResponse> getContent(UUID id) {
        var item = service.get(id);
        return ResponseEntity.ok(response(visible(item, !item.premiumLocked() || hasPremium())));
    }

    @Override
    public ResponseEntity<ContentListResponse> listContent(Integer page, Integer size, UUID categoryId, String type,
                                                            String difficulty, Boolean premium, String query, String sort) {
        var result = service.list(page, size, categoryId, type, difficulty, premium, query, sort);
        boolean access = result.items().stream().anyMatch(ContentItem::premiumLocked) && hasPremium();
        var body = new ContentListResponse(result.items().stream().map(item -> data(visible(item, access))).toList(),
                new PageMetadataResponse(result.page(), result.size(), result.total()));
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total())).body(body);
    }

    @Override public ResponseEntity<FormatListResponse> listFormats() { return ResponseEntity.ok(new FormatListResponse(service.formats())); }

    @Override public ResponseEntity<ContentResponse> updateContent(UUID id, ContentRequest request) {
        return ResponseEntity.ok(response(service.update(id, draft(request))));
    }

    @Override public ResponseEntity<Void> archiveContent(UUID id) { service.archive(id); return ResponseEntity.noContent().build(); }

    private boolean hasPremium() { return premium.premiumFor(request.getHeader("X-User-Id")); }

    private static ContentItem visible(ContentItem item, boolean access) {
        if (!item.premiumLocked() || access) return item;
        return new ContentItem(item.id(), item.categoryId(), item.type(), item.title(), "Premium content",
                "Premium content", null, item.difficulty(), item.estimatedMinutes(), true, item.status(), item.tags(), item.createdAt());
    }

    private static ContentItemService.Draft draft(ContentRequest value) {
        return new ContentItemService.Draft(value.getCategoryId(), value.getType().getValue(), value.getTitle(),
                value.getDescription(), value.getBody(), value.getMediaUrl(), value.getDifficulty().getValue(),
                value.getEstimatedMinutes(), value.getPremiumLocked(), value.getTags());
    }

    private static ContentResponse response(ContentItem item) { return new ContentResponse(data(item)); }

    private static ContentDataResponse data(ContentItem item) {
        return new ContentDataResponse(item.categoryId(), ContentDataResponse.TypeEnum.fromValue(item.type()), item.title(),
                item.description(), item.body(), ContentDataResponse.DifficultyEnum.fromValue(item.difficulty()),
                item.estimatedMinutes(), item.premiumLocked(), item.tags(), item.id(), item.status(),
                item.createdAt().atOffset(ZoneOffset.UTC)).mediaUrl(item.mediaUrl());
    }
}
