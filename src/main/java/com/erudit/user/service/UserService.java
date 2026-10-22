package com.erudit.user.service;

import com.erudit.user.model.User;
import com.erudit.user.model.UserStatus;
import com.erudit.user.dto.RegisterRequest;
import com.erudit.user.dto.UserProfileResponse;
import com.erudit.user.exception.EmailAlreadyExistsException;
import com.erudit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final EmailVerificationService emailVerificationService;


    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordHasher.encode(request.password()))
                .name(request.name())
                .build();

        User savedUser = userRepository.saveAndFlush(user);

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

        userRepository.save(user);
    }
}
