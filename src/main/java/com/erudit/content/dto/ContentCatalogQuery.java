package com.erudit.content.dto;

import com.erudit.content.model.ContentType;
import com.erudit.content.model.Difficulty;

import java.util.UUID;

public record ContentCatalogQuery(int page, int size, UUID categoryId, ContentType type,
                                  Difficulty difficulty, Boolean premium, String query, Sort sort) {
    public enum Sort {
        CREATED_DESC("c.created_at DESC"), CREATED_ASC("c.created_at ASC"),
        TITLE_ASC("LOWER(c.title) ASC"), TITLE_DESC("LOWER(c.title) DESC"),
        DURATION_ASC("c.estimated_minutes ASC"), DURATION_DESC("c.estimated_minutes DESC");

        private final String sql;
        Sort(String sql) { this.sql = sql; }
        public String sql() { return sql; }
    }
}
