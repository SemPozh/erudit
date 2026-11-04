package com.erudit.content.repository;

import com.erudit.content.model.ContentItem;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentItemRepository {
    private final JdbcClient jdbc;


    public void lock(UUID id) {
        if (jdbc.sql("SELECT id FROM content WHERE id=:id FOR UPDATE").param("id",id).query(UUID.class).optional().isEmpty())
            throw new com.erudit.content.service.CategoryNotFoundException(id);
    }
    public List<ContentItem> pending(int page,int size) {
        return jdbc.sql("SELECT * FROM content WHERE status='PENDING_MODERATION' ORDER BY created_at,id LIMIT :size OFFSET :offset")
                .param("size",size).param("offset",(long)page*size).query(this::map).list();
    }
    public List<ContentItem> feed(UUID user,int page,int size) {
        return jdbc.sql("""
                SELECT c.* FROM content c
                LEFT JOIN content_progress own ON own.content_id=c.id AND own.user_id=:user
                LEFT JOIN (SELECT interacted.category_id,count(*) interactions FROM content_progress p
                  JOIN content interacted ON interacted.id=p.content_id WHERE p.user_id=:user GROUP BY interacted.category_id)
                  behavior ON behavior.category_id=c.category_id
                WHERE c.status='PUBLISHED'
                ORDER BY (COALESCE(behavior.interactions,0)*10 + CASE own.status WHEN 'COMPLETED' THEN -1000 WHEN 'VIEWED' THEN -100 ELSE 0 END) DESC,
                  c.created_at DESC,c.id
                LIMIT :limit OFFSET :offset
                """).param("user",user).param("limit",size+1).param("offset",(long)page*size).query(this::map).list();
    }
    public void save(ContentItem item) {
        jdbc.sql("""
                INSERT INTO content(id,category_id,type,title,description,body,media_url,difficulty,
                  estimated_minutes,premium_locked,status,created_at,updated_at)
                VALUES(:id,:categoryId,:type,:title,:description,:body,:mediaUrl,:difficulty,
                  :estimatedMinutes,:premiumLocked,:status,:createdAt,:createdAt)
                """).param("id", item.id()).param("categoryId", item.categoryId()).param("type", item.type())
                .param("title", item.title()).param("description", item.description()).param("body", item.body())
                .param("mediaUrl", item.mediaUrl()).param("difficulty", item.difficulty())
                .param("estimatedMinutes", item.estimatedMinutes()).param("premiumLocked", item.premiumLocked())
                .param("status", item.status()).param("createdAt", Timestamp.from(item.createdAt())).update();
        replaceTags(item.id(), item.tags());
    }

    public Optional<ContentItem> find(UUID id) {
        return jdbc.sql("SELECT * FROM content WHERE id=:id").param("id", id).query(this::map).optional();
    }

    public List<ContentItem> list(int page, int size, UUID categoryId, String type, String difficulty,
                                  Boolean premium, String query, String order) {
        String sql = "SELECT * FROM content WHERE status='PUBLISHED' "
                + (categoryId == null ? "" : "AND category_id=:categoryId ")
                + (type == null ? "" : "AND type=:type ")
                + (difficulty == null ? "" : "AND difficulty=:difficulty ")
                + (premium == null ? "" : "AND premium_locked=:premium ")
                + (query == null || query.isBlank() ? "" : "AND (lower(title) LIKE :query OR lower(description) LIKE :query) ")
                + "ORDER BY " + order + " LIMIT :size OFFSET :offset";
        var spec = jdbc.sql(sql).param("size", size).param("offset", page * size);
        if (categoryId != null) spec = spec.param("categoryId", categoryId);
        if (type != null) spec = spec.param("type", type);
        if (difficulty != null) spec = spec.param("difficulty", difficulty);
        if (premium != null) spec = spec.param("premium", premium);
        if (query != null && !query.isBlank()) spec = spec.param("query", "%" + query.toLowerCase() + "%");
        return spec.query(this::map).list();
    }

    public long count(UUID categoryId, String type, String difficulty, Boolean premium, String query) {
        String sql = "SELECT COUNT(*) FROM content WHERE status='PUBLISHED' "
                + (categoryId == null ? "" : "AND category_id=:categoryId ")
                + (type == null ? "" : "AND type=:type ")
                + (difficulty == null ? "" : "AND difficulty=:difficulty ")
                + (premium == null ? "" : "AND premium_locked=:premium ")
                + (query == null || query.isBlank() ? "" : "AND (lower(title) LIKE :query OR lower(description) LIKE :query)");
        var spec = jdbc.sql(sql);
        if (categoryId != null) spec = spec.param("categoryId", categoryId);
        if (type != null) spec = spec.param("type", type);
        if (difficulty != null) spec = spec.param("difficulty", difficulty);
        if (premium != null) spec = spec.param("premium", premium);
        if (query != null && !query.isBlank()) spec = spec.param("query", "%" + query.toLowerCase() + "%");
        return spec.query(Long.class).single();
    }

    public void update(ContentItem item) {
        jdbc.sql("""
                UPDATE content SET category_id=:categoryId,type=:type,title=:title,description=:description,
                body=:body,media_url=:mediaUrl,difficulty=:difficulty,estimated_minutes=:estimatedMinutes,
                premium_locked=:premiumLocked,status='PENDING_MODERATION',updated_at=now() WHERE id=:id""")
                .param("id", item.id()).param("categoryId", item.categoryId()).param("type", item.type())
                .param("title", item.title()).param("description", item.description()).param("body", item.body())
                .param("mediaUrl", item.mediaUrl()).param("difficulty", item.difficulty())
                .param("estimatedMinutes", item.estimatedMinutes()).param("premiumLocked", item.premiumLocked()).update();
        replaceTags(item.id(), item.tags());
    }

    public boolean archive(UUID id) {
        return jdbc.sql("UPDATE content SET status='ARCHIVED',updated_at=now() WHERE id=:id")
                .param("id", id).update() == 1;
    }

    private void replaceTags(UUID id, List<String> tags) {
        jdbc.sql("DELETE FROM content_tags WHERE content_id=:id").param("id", id).update();
        tags.stream().distinct().forEach(tag -> jdbc.sql("INSERT INTO content_tags(content_id,tag) VALUES(:id,:tag)")
                .param("id", id).param("tag", tag).update());
    }

    private ContentItem map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<String> tags = jdbc.sql("SELECT tag FROM content_tags WHERE content_id=:id ORDER BY tag")
                .param("id", id).query(String.class).list();
        return new ContentItem(id, rs.getObject("category_id", UUID.class), rs.getString("type"),
                rs.getString("title"), rs.getString("description"), rs.getString("body"), rs.getString("media_url"),
                rs.getString("difficulty"), rs.getInt("estimated_minutes"), rs.getBoolean("premium_locked"),
                rs.getString("status"), tags, rs.getTimestamp("created_at").toInstant());
    }
}
