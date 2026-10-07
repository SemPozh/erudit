package com.erudit.user.repository;

import com.erudit.user.domain.DevicePushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevicePushTokenRepository extends JpaRepository<DevicePushToken, UUID> {
    Optional<DevicePushToken> findByToken(String token);
    Optional<DevicePushToken> findByIdAndUserId(UUID id, UUID userId);
    List<DevicePushToken> findAllByUserIdAndActiveTrue(UUID userId);
}
