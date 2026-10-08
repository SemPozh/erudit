package com.erudit.media.repository;

import com.erudit.media.model.MediaAsset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MediaRepository {
    private final JdbcTemplate jdbc;

    public MediaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(MediaAsset media) {
        jdbc.update("""
                INSERT INTO media_files (id, storage_key, original_name, content_type, size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, media.id(), media.storageKey(), media.originalName(), media.contentType(), media.size(),
                Timestamp.from(media.createdAt()));
    }

    public Optional<MediaAsset> findById(UUID id) {
        return jdbc.query("SELECT * FROM media_files WHERE id = ?", (rs, row) -> new MediaAsset(
                rs.getObject("id", UUID.class), rs.getString("storage_key"), rs.getString("original_name"),
                rs.getString("content_type"), rs.getLong("size_bytes"),
                rs.getTimestamp("created_at").toInstant()), id).stream().findFirst();
    }
}
