package com.erudit.user.service;

import com.erudit.user.domain.DevicePushToken;
import com.erudit.user.domain.PushPlatform;
import com.erudit.user.repository.DevicePushTokenRepository;
import com.erudit.user.repository.UserRepository;
import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DevicePushTokenService {
    private final DevicePushTokenRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public void register(UUID userId, String rawToken, String rawPlatform) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
        String token = normalizeToken(rawToken);
        PushPlatform platform = parsePlatform(rawPlatform);
        Instant now = clock.instant();
        DevicePushToken value = repository.findByToken(token).orElseGet(() -> DevicePushToken.builder()
                .id(UUID.randomUUID())
                .token(token)
                .createdAt(now)
                .build());
        value.setUserId(userId);
        value.setPlatform(platform);
        value.setActive(true);
        value.setUpdatedAt(now);
        repository.save(value);
    }

    @Transactional
    public void revoke(UUID userId, UUID tokenId) {
        DevicePushToken value = repository.findByIdAndUserId(tokenId, userId)
                .orElseThrow(() -> new NotFoundException("Push token not found"));
        value.setActive(false);
        value.setUpdatedAt(clock.instant());
        repository.save(value);
    }

    @Transactional(readOnly = true)
    public List<DevicePushToken> activeTokens(UUID userId) {
        return repository.findAllByUserIdAndActiveTrue(userId);
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank() || value.length() > 4096) {
            throw new ValidationException("Push token must contain between 1 and 4096 characters");
        }
        return value.trim();
    }

    private static PushPlatform parsePlatform(String value) {
        try {
            return PushPlatform.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ValidationException("Platform must be ANDROID, IOS or WEB");
        }
    }
}
