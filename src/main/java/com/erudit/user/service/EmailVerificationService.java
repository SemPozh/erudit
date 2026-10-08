package com.erudit.user.service;

import com.erudit.user.config.VerificationProperties;
import com.erudit.user.model.EmailVerificationToken;
import com.erudit.user.model.User;
import com.erudit.user.exception.ExpiredVerificationTokenException;
import com.erudit.user.exception.InvalidVerificationTokenException;
import com.erudit.user.service.EmailSender;
import com.erudit.user.repository.EmailVerificationTokenRepository;
import com.erudit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final VerificationTokenService tokenService;
    private final EmailSender emailSender;
    private final VerificationProperties properties;

    /**
     * Creates (or refreshes) a verification token for the user, stores its hash,
     * and emails the raw token link. Runs inside the caller's transaction.
     */
    @Transactional
    public void issueFor(User user) {
        LocalDateTime now = LocalDateTime.now();

        // Invalidate any outstanding tokens for this user.
        tokenRepository.invalidateAllForUser(user.getId(), now);

        String raw = tokenService.generateRawToken();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenService.hash(raw))
                .expiresAt(now.plus(properties.tokenTtl()))
                .build();
        tokenRepository.save(token);

        String link = buildLink(raw);
        emailSender.sendVerificationEmail(user.getEmail(), user.getName(), link);
    }

    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidVerificationTokenException("Verification token is required");
        }

        String hash = tokenService.hash(rawToken);
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidVerificationTokenException("Verification token is unknown"));

        LocalDateTime now = LocalDateTime.now();

        if (token.isUsed()) {
            throw new ExpiredVerificationTokenException("Verification token already used");
        }
        if (token.isExpired(now)) {
            throw new ExpiredVerificationTokenException("Verification token has expired");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsedAt(now);
        tokenRepository.save(token);
    }

    private String buildLink(String rawToken) {
        String base = properties.baseUrl().replaceAll("/+$", "");
        return base + "/verify-email?token=" + rawToken;
    }
}