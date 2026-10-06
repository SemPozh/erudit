package com.erudit.content;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContentRepository {
    private final JdbcTemplate jdbc;

    public ContentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveCategory(Category category) {
        jdbc.update("INSERT INTO categories (id, name) VALUES (?, ?)", category.id(), category.name());
    }

    public Optional<Category> findCategory(UUID id) {
        return jdbc.query("SELECT id, name FROM categories WHERE id = ?",
                (rs, row) -> new Category(rs.getObject("id", UUID.class), rs.getString("name")), id)
                .stream().findFirst();
    }

    public void save(Content content) {
        jdbc.update("""
                INSERT INTO content (id, category_id, type, title, description, body,
                    media_url, difficulty, estimated_minutes, author_id, status, created_at, premium_locked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, content.id(), content.categoryId(), content.type().name(), content.title(),
                content.description(), content.body(), content.mediaUrl(), content.difficulty().name(),
                content.estimatedMinutes(), content.authorId(), content.status().name(),
                Timestamp.from(content.createdAt()), content.premiumLocked());
        for (String tag : content.tags()) {
            jdbc.update("INSERT INTO content_tags (content_id, tag) VALUES (?, ?)", content.id(), tag);
        }
    }

    public void update(Content content) {
        jdbc.update("""
                UPDATE content
                SET category_id = ?, type = ?, title = ?, description = ?, body = ?,
                    media_url = ?, difficulty = ?, estimated_minutes = ?, premium_locked = ?
                WHERE id = ?
                """, content.categoryId(), content.type().name(), content.title(),
                content.description(), content.body(), content.mediaUrl(), content.difficulty().name(),
                content.estimatedMinutes(), content.premiumLocked(), content.id());
        jdbc.update("DELETE FROM content_tags WHERE content_id = ?", content.id());
        for (String tag : content.tags()) {
            jdbc.update("INSERT INTO content_tags (content_id, tag) VALUES (?, ?)", content.id(), tag);
        }
    }

    public void updateStatus(UUID id, ContentStatus status) {
        jdbc.update("UPDATE content SET status = ? WHERE id = ?", status.name(), id);
    }

    public List<Content> findByStatus(ContentStatus status, int page, int size) {
        return jdbc.queryForList("SELECT id FROM content WHERE status = ? ORDER BY created_at, id LIMIT ? OFFSET ?",
                        UUID.class, status.name(), size, page * size).stream()
                .map(id -> findById(id).orElseThrow()).toList();
    }

    public long countByStatus(ContentStatus status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE status = ?", Long.class, status.name());
        return count == null ? 0 : count;
    }

    public Optional<Content> findById(UUID id) {
        List<Content> rows = jdbc.query("SELECT * FROM content WHERE id = ?", (rs, row) -> new Content(
                rs.getObject("id", UUID.class),
                rs.getObject("category_id", UUID.class),
                ContentType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("body"),
                rs.getString("media_url"),
                Difficulty.valueOf(rs.getString("difficulty")),
                rs.getInt("estimated_minutes"),
                rs.getString("author_id"),
                ContentStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                jdbc.queryForList("SELECT tag FROM content_tags WHERE content_id = ? ORDER BY tag", String.class, id),
                rs.getBoolean("premium_locked")), id);
        return rows.stream().findFirst();
    }

    public List<Category> findCategories(int page, int size) {
        return jdbc.query("SELECT id, name FROM categories ORDER BY name LIMIT ? OFFSET ?",
                (rs, row) -> new Category(rs.getObject("id", UUID.class), rs.getString("name")),
                size, page * size);
    }

    public long countCategories() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM categories", Long.class);
        return count == null ? 0 : count;
    }
}
