package com.erudit.user.service;

import com.erudit.user.domain.User;
import com.erudit.user.domain.UserStatus;
import com.erudit.user.dto.RegisterRequest;
import com.erudit.user.dto.UserProfileResponse;
import com.erudit.user.exception.EmailAlreadyExistsException;
import com.erudit.user.repository.RefreshTokenRepository;
import com.erudit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;


    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build();

        User savedUser = userRepository.save(user);

        emailVerificationService.issueFor(savedUser);

        return mapToProfileResponse(savedUser);
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatar(),
                user.getCreatedAt(),
                user.getStatus().name()
        );
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setStatus(UserStatus.DELETED);

        refreshTokenRepository.revokeAllActiveForUser(
                userId,
                LocalDateTime.now(clock)
        );

        userRepository.save(user);
    }
}
