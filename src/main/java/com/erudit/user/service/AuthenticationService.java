package com.erudit.user.service;

import com.erudit.user.config.AuthProperties;
import com.erudit.user.domain.RefreshToken;
import com.erudit.user.domain.User;
import com.erudit.user.domain.UserStatus;
import com.erudit.user.repository.RefreshTokenRepository;
import com.erudit.user.repository.UserRepository;
import com.erudit.web.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties properties;
    private final Clock clock;

    @Transactional
    public IssuedTokenPair login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        return issuePair(user);
    }

    @Transactional
    public IssuedTokenPair refresh(String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .filter(token -> token.getExpiresAt().isAfter(now))
                .filter(token -> token.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        TokenMaterial replacement = newRefreshToken(current.getUser(), now);
        RefreshToken savedReplacement = refreshTokenRepository.save(replacement.entity());
        current.setRevokedAt(now);
        current.setReplacedBy(savedReplacement);

        return new IssuedTokenPair(jwtTokenService.issueAccessToken(current.getUser()), replacement.raw(),
                jwtTokenService.accessTtlSeconds());
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now(clock));
                    refreshTokenRepository.save(token);
                });
    }

    private IssuedTokenPair issuePair(User user) {
        TokenMaterial refresh = newRefreshToken(user, LocalDateTime.now(clock));
        refreshTokenRepository.save(refresh.entity());
        return new IssuedTokenPair(jwtTokenService.issueAccessToken(user), refresh.raw(),
                jwtTokenService.accessTtlSeconds());
    }

    private TokenMaterial newRefreshToken(User user, LocalDateTime now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .createdAt(now)
                .expiresAt(now.plus(properties.refreshTtl()))
                .build();
        return new TokenMaterial(raw, entity);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash refresh token", exception);
        }
    }

    private record TokenMaterial(String raw, RefreshToken entity) {
    }


    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(
                userId,
                LocalDateTime.now(clock)
        );
    }

}
