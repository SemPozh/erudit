package com.erudit.content.repository;

import com.erudit.content.model.MediaAsset;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MediaRepository {
    private final JdbcClient jdbc;
    public void save(MediaAsset media) {
        jdbc.sql("INSERT INTO media_files VALUES(:id,:storageKey,:originalName,:contentType,:size,:createdAt)")
                .param("id", media.id()).param("storageKey", media.storageKey())
                .param("originalName", media.originalName()).param("contentType", media.contentType())
                .param("size", media.size()).param("createdAt", Timestamp.from(media.createdAt())).update();
    }
    public Optional<MediaAsset> find(UUID id) {
        return jdbc.sql("SELECT * FROM media_files WHERE id=:id").param("id", id).query((rs, row) ->
                new MediaAsset(id, rs.getString("storage_key"), rs.getString("original_name"),
                        rs.getString("content_type"), rs.getLong("size_bytes"), rs.getTimestamp("created_at").toInstant())).optional();
    }
}
