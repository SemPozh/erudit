package com.erudit.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String name,
        String avatar,
        LocalDateTime createdAt,
        String status
) {}
