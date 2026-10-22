package com.erudit.user.config;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * Resolves the temporary, unverified request identity used by laboratory work 1.
 */
public final class LabOneRequestIdentity {
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private LabOneRequestIdentity() {
    }

    public static String name(HttpServletRequest request) {
        String header = request.getHeader(USER_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header.strip();
        }
        return request.getUserPrincipal() == null || request.getUserPrincipal().getName().isBlank()
                ? DEFAULT_USER_ID.toString()
                : request.getUserPrincipal().getName();
    }

    public static UUID userId(HttpServletRequest request) {
        try {
            return UUID.fromString(name(request));
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_USER_ID;
        }
    }
}
