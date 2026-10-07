package com.erudit.program;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class ProgramPreferenceRepository {
    private final JdbcTemplate jdbc;

    public ProgramPreferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Integer> favoriteTopics(UUID userId) {
        return jdbc.query("""
                SELECT c.name FROM user_favorite_categories f
                JOIN categories c ON c.id = f.category_id
                WHERE f.user_id = ?
                """, (rs, row) -> rs.getString("name"), userId).stream()
                .collect(Collectors.toMap(value -> value, ignored -> 100, Math::max));
    }

    public Map<String, Integer> behavioralTopics(String userId) {
        return jdbc.query("""
                SELECT cat.name, COUNT(*) interactions
                FROM content_progress cp
                JOIN content c ON c.id = cp.content_id
                JOIN categories cat ON cat.id = c.category_id
                WHERE cp.user_id = ?
                GROUP BY cat.name
                """, (rs, row) -> Map.entry(rs.getString("name"), Math.min(40, rs.getInt("interactions") * 10)),
                userId).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Math::max));
    }
}

