package com.erudit.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;

/**
 * Supplies an unverified request identity while the project implements laboratory work 1.
 * Authentication and role authorization are deliberately deferred until laboratory work 3.
 */
@Component
public class LabOneRequestIdentityFilter extends OncePerRequestFilter {
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new RequestIdentity(request, requestUserId(request)), response);
    }

    private static String requestUserId(HttpServletRequest request) {
        String value = request.getHeader(USER_ID_HEADER);
        if (value == null || value.isBlank()) {
            return request.getUserPrincipal() == null ? DEFAULT_USER_ID : request.getUserPrincipal().getName();
        }
        return value.strip();
    }

    private static final class RequestIdentity extends HttpServletRequestWrapper {
        private final Principal principal;

        private RequestIdentity(HttpServletRequest request, String userId) {
            super(request);
            this.principal = () -> userId;
        }

        @Override
        public Principal getUserPrincipal() {
            return principal;
        }

        @Override
        public String getRemoteUser() {
            return principal.getName();
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public String getAuthType() {
            return null;
        }
    }
}
