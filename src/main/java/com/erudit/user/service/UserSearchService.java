package com.erudit.user.service;

import com.erudit.user.domain.User;
import com.erudit.user.repository.UserRepository;
import com.erudit.web.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSearchService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<User> search(UUID viewerId, String rawQuery, Integer requestedPage, Integer requestedSize) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty() || query.length() > 255) {
            throw new ValidationException("Search query must contain between 1 and 255 characters");
        }
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? 20 : requestedSize;
        if (page < 0 || size < 1 || size > 50) {
            throw new ValidationException("page must be non-negative and size must be between 1 and 50");
        }
        String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
        return userRepository.searchVisible(viewerId, pattern, PageRequest.of(page, size));
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
