package com.erudit.user.repository;

import com.erudit.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface      UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    @Query(value = """
            SELECT u.* FROM users u
            LEFT JOIN user_settings s ON s.user_id = u.id
            WHERE u.id <> :viewerId
              AND u.status = 'ACTIVE'
              AND COALESCE(s.visible_in_search, TRUE) = TRUE
              AND (LOWER(u.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(u.email) LIKE :pattern ESCAPE '!')
            ORDER BY LOWER(u.name), u.id
            """, countQuery = """
            SELECT COUNT(*) FROM users u
            LEFT JOIN user_settings s ON s.user_id = u.id
            WHERE u.id <> :viewerId
              AND u.status = 'ACTIVE'
              AND COALESCE(s.visible_in_search, TRUE) = TRUE
              AND (LOWER(u.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(u.email) LIKE :pattern ESCAPE '!')
            """, nativeQuery = true)
    Page<User> searchVisible(@Param("viewerId") UUID viewerId,
                             @Param("pattern") String pattern,
                             Pageable pageable);
}
