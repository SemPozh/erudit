package com.erudit.assessment.repository;

import com.erudit.assessment.model.Grade;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GradeRepository {
    private final JdbcTemplate jdbc;

    public GradeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Grade> findByScore(int erScore) {
        return jdbc.query("""
                SELECT code, title, min_score, max_score
                FROM assessment_grade_definitions
                WHERE ? BETWEEN min_score AND max_score
                """, (rs, row) -> new Grade(rs.getString("code"), rs.getString("title"),
                rs.getInt("min_score"), rs.getInt("max_score")), erScore).stream().findFirst();
    }
}
