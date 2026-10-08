package com.erudit.user.service;

import com.erudit.user.config.PasswordResetProperties;
import com.erudit.user.model.PasswordResetToken;
import com.erudit.user.model.User;
import com.erudit.user.service.EmailSender;
import com.erudit.user.repository.PasswordResetTokenRepository;
import com.erudit.user.repository.RefreshTokenRepository;
import com.erudit.user.repository.UserRepository;
import com.erudit.web.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final PasswordResetProperties properties;
    private final Clock clock;

    @Transactional
    public void request(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(this::issueFor);
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ValidationException("Password reset token is required");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token = resetTokenRepository.findByHashForUpdate(tokenService.hash(rawToken))
                .filter(candidate -> candidate.getUsedAt() == null)
                .filter(candidate -> candidate.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ValidationException("Password reset token is invalid or expired"));

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        token.setUsedAt(now);
        resetTokenRepository.save(token);
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
    }

    private void issueFor(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        resetTokenRepository.invalidateActiveForUser(user.getId(), now);
        String rawToken = tokenService.generateRawToken();
        resetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenService.hash(rawToken))
                .createdAt(now)
                .expiresAt(now.plus(properties.tokenTtl()))
                .build());
        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        emailSender.sendPasswordResetEmail(user.getEmail(), user.getName(),
                baseUrl + "/reset-password?token=" + rawToken);
    }
}
