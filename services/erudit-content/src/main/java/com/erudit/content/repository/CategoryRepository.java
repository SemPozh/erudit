package com.erudit.content.repository;

import com.erudit.content.model.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CategoryRepository {
    private final JdbcClient jdbc;

    public Category create(Category category) {
        jdbc.sql("INSERT INTO categories (id, name) VALUES (:id, :name)")
                .param("id", category.id()).param("name", category.name()).update();
        return category;
    }

    public Optional<Category> findById(UUID id) {
        return jdbc.sql("SELECT id, name FROM categories WHERE id = :id").param("id", id)
                .query((rs, row) -> new Category(rs.getObject("id", UUID.class), rs.getString("name")))
                .optional();
    }

    public List<Category> findAll(int page, int size) {
        return jdbc.sql("SELECT id, name FROM categories ORDER BY name, id LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", page * size)
                .query((rs, row) -> new Category(rs.getObject("id", UUID.class), rs.getString("name"))).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM categories").query(Long.class).single();
    }

    public Optional<Category> update(UUID id, String name) {
        int updated = jdbc.sql("UPDATE categories SET name = :name WHERE id = :id")
                .param("id", id).param("name", name).update();
        return updated == 0 ? Optional.empty() : Optional.of(new Category(id, name));
    }

    public boolean delete(UUID id) throws DataIntegrityViolationException {
        return jdbc.sql("DELETE FROM categories WHERE id = :id").param("id", id).update() == 1;
    }
}
