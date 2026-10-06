package com.erudit.user.service;

import com.erudit.media.MediaAsset;
import com.erudit.media.MediaRepository;
import com.erudit.user.domain.User;
import com.erudit.user.repository.UserRepository;
import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return findUser(userId);
    }

    @Transactional
    public User update(UUID userId, String name, UUID avatarId) {
        if ((name == null || name.isBlank()) && avatarId == null) {
            throw new ValidationException("At least one profile field must be provided");
        }
        User user = findUser(userId);
        if (name != null) {
            String normalized = name.trim();
            if (normalized.length() < 2 || normalized.length() > 100) {
                throw new ValidationException("Name must contain from 2 to 100 characters");
            }
            user.setName(normalized);
        }
        if (avatarId != null) {
            MediaAsset avatar = mediaRepository.findById(avatarId)
                    .orElseThrow(() -> new NotFoundException("Avatar media not found"));
            if (!avatar.contentType().startsWith("image/")) {
                throw new ValidationException("Avatar must be an image");
            }
            user.setAvatar("/api/v1/media/" + avatar.id());
        }
        return userRepository.save(user);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
