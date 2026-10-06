package com.erudit.user.service;

import com.erudit.user.domain.UserRole;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, Set<UserRole> roles) {
}
