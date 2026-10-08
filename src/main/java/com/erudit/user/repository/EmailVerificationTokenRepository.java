package com.erudit.user.repository;

import com.erudit.user.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update EmailVerificationToken t
               set t.usedAt = :now
             where t.user.id = :userId
               and t.usedAt is null
            """)
    int invalidateAllForUser(@Param("userId") UUID userId,
                             @Param("now") LocalDateTime now);

    long countByUserIdAndUsedAtIsNull(UUID userId);
}