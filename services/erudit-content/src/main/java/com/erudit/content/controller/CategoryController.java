package com.erudit.content.controller;

import com.erudit.content.model.Category;
import com.erudit.content.openapi.api.CategoriesApi;
import com.erudit.content.openapi.model.CategoryDataResponse;
import com.erudit.content.openapi.model.CategoryListResponse;
import com.erudit.content.openapi.model.CategoryRequest;
import com.erudit.content.openapi.model.CategoryResponse;
import com.erudit.content.openapi.model.PageMetadataResponse;
import com.erudit.content.service.CategoryService;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CategoryController implements CategoriesApi {
    private final CategoryService service;

    @Override
    public ResponseEntity<CategoryResponse> createCategory(CategoryRequest request) {
        Category category = service.create(request.getName());
        return ResponseEntity.created(URI.create("/api/v1/content/categories/" + category.id()))
                .body(response(category));
    }

    @Override
    public ResponseEntity<Void> deleteCategory(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CategoryResponse> getCategory(UUID id) {
        return ResponseEntity.ok(response(service.get(id)));
    }

    @Override
    public ResponseEntity<CategoryListResponse> listCategories(Integer page, Integer size) {
        var result = service.list(page, size);
        var data = result.items().stream().map(CategoryController::data).toList();
        var pagination = new PageMetadataResponse(result.page(), result.size(), result.total());
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(new CategoryListResponse(data, pagination));
    }

    @Override
    public ResponseEntity<CategoryResponse> updateCategory(UUID id, CategoryRequest request) {
        return ResponseEntity.ok(response(service.update(id, request.getName())));
    }

    private static CategoryResponse response(Category category) {
        return new CategoryResponse(data(category));
    }

    private static CategoryDataResponse data(Category category) {
        return new CategoryDataResponse(category.id(), category.name());
    }
}
