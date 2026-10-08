package com.erudit.content.repository;

import com.erudit.content.dto.ContentCatalogQuery;
import com.erudit.content.dto.ContentPage;
import com.erudit.content.model.Content;
import com.erudit.content.model.ContentSlice;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.model.ContentType;
import com.erudit.content.model.Difficulty;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ContentCatalogRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ContentCatalogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ContentPage find(ContentCatalogQuery query) {
        StringBuilder where = new StringBuilder(" WHERE c.status = 'PUBLISHED'");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        addFilters(query, where, parameters);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM content c" + where, parameters, Long.class);
        parameters.addValue("limit", query.size()).addValue("offset", query.page() * query.size());
        List<Content> rows = jdbc.query("SELECT c.* FROM content c" + where +
                        " ORDER BY " + query.sort().sql() + ", c.id ASC LIMIT :limit OFFSET :offset",
                parameters, (rs, row) -> new Content(rs.getObject("id", UUID.class),
                        rs.getObject("category_id", UUID.class), ContentType.valueOf(rs.getString("type")),
                        rs.getString("title"), rs.getString("description"), rs.getString("body"),
                        rs.getString("media_url"), Difficulty.valueOf(rs.getString("difficulty")),
                        rs.getInt("estimated_minutes"), rs.getString("author_id"),
                        ContentStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(),
                        List.of(), rs.getBoolean("premium_locked")));
        return new ContentPage(withTags(rows), total == null ? 0 : total, query.page(), query.size());
    }

    public ContentSlice personalizedFeed(UUID userId, Set<UUID> favoriteCategories, int page, int size) {
        var parameters = new MapSqlParameterSource()
                .addValue("userId", userId.toString())
                .addValue("favorites", favoriteCategories.isEmpty()
                        ? List.of(new UUID(0, 0)) : favoriteCategories)
                .addValue("limit", size + 1)
                .addValue("offset", page * size);
        String sql = """
                SELECT c.*
                FROM content c
                LEFT JOIN content_progress own_progress
                    ON own_progress.content_id = c.id AND own_progress.user_id = :userId
                LEFT JOIN (
                    SELECT interacted.category_id, COUNT(*) interaction_count
                    FROM content_progress progress
                    JOIN content interacted ON interacted.id = progress.content_id
                    WHERE progress.user_id = :userId
                    GROUP BY interacted.category_id
                ) behavior ON behavior.category_id = c.category_id
                WHERE c.status = 'PUBLISHED'
                ORDER BY (
                    CASE WHEN c.category_id IN (:favorites) THEN 100 ELSE 0 END
                    + COALESCE(behavior.interaction_count, 0) * 10
                    + CASE own_progress.status
                        WHEN 'COMPLETED' THEN -1000
                        WHEN 'VIEWED' THEN -100
                        ELSE 0
                      END
                ) DESC, c.created_at DESC, c.id
                LIMIT :limit OFFSET :offset
                """;
        List<Content> rows = jdbc.query(sql, parameters, (rs, row) -> new Content(
                rs.getObject("id", UUID.class), rs.getObject("category_id", UUID.class),
                ContentType.valueOf(rs.getString("type")), rs.getString("title"),
                rs.getString("description"), rs.getString("body"), rs.getString("media_url"),
                Difficulty.valueOf(rs.getString("difficulty")), rs.getInt("estimated_minutes"),
                rs.getString("author_id"), ContentStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(), List.of(), rs.getBoolean("premium_locked")));
        boolean hasMore = rows.size() > size;
        List<Content> pageItems = hasMore ? rows.subList(0, size) : rows;
        return new ContentSlice(withTags(pageItems), page, size, hasMore);
    }

    private List<Content> withTags(List<Content> rows) {
        if (rows.isEmpty()) return rows;
        List<UUID> ids = rows.stream().map(Content::id).toList();
        Map<UUID, List<String>> tags = new HashMap<>();
        var tagRows = jdbc.query("SELECT content_id, tag FROM content_tags WHERE content_id IN (:ids) ORDER BY tag",
                Map.of("ids", ids), (rs, row) -> Map.entry(
                        rs.getObject("content_id", UUID.class), rs.getString("tag")));
        tagRows.forEach(entry -> tags.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                .add(entry.getValue()));
        return rows.stream().map(content -> new Content(content.id(), content.categoryId(), content.type(),
                content.title(), content.description(), content.body(), content.mediaUrl(), content.difficulty(),
                content.estimatedMinutes(), content.authorId(), content.status(), content.createdAt(),
                List.copyOf(tags.getOrDefault(content.id(), List.of())), content.premiumLocked())).toList();
    }

    private static void addFilters(ContentCatalogQuery query, StringBuilder where,
                                   MapSqlParameterSource parameters) {
        if (query.categoryId() != null) { where.append(" AND c.category_id = :categoryId"); parameters.addValue("categoryId", query.categoryId()); }
        if (query.type() != null) { where.append(" AND c.type = :type"); parameters.addValue("type", query.type().name()); }
        if (query.difficulty() != null) { where.append(" AND c.difficulty = :difficulty"); parameters.addValue("difficulty", query.difficulty().name()); }
        if (query.premium() != null) { where.append(" AND c.premium_locked = :premium"); parameters.addValue("premium", query.premium()); }
        if (query.query() != null && !query.query().isBlank()) {
            where.append(" AND (LOWER(c.title) LIKE :query OR LOWER(c.description) LIKE :query)");
            parameters.addValue("query", "%" + query.query().trim().toLowerCase(Locale.ROOT) + "%");
        }
    }
}
