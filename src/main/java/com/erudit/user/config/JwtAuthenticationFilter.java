package com.erudit.user.config;

import com.erudit.user.service.AuthenticatedUser;
import com.erudit.user.service.InvalidAccessTokenException;
import com.erudit.user.service.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectProvider<JwtTokenService> jwtTokenServiceProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            JwtTokenService jwtTokenService = jwtTokenServiceProvider.getIfAvailable();
            if (jwtTokenService == null) {
                throw new InvalidAccessTokenException("JWT verification is unavailable");
            }
            AuthenticatedUser user = jwtTokenService.verifyAccessToken(authorization.substring(7));
            chain.doFilter(new AuthenticatedRequest(request, user), response);
        } catch (InvalidAccessTokenException exception) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of("error", Map.of(
                    "code", "UNAUTHORIZED", "message", "Invalid or expired access token")));
        }
    }

    private static final class AuthenticatedRequest extends HttpServletRequestWrapper {
        private final AuthenticatedUser user;
        private final Principal principal;

        private AuthenticatedRequest(HttpServletRequest request, AuthenticatedUser user) {
            super(request);
            this.user = user;
            this.principal = () -> user.id().toString();
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
            String normalized = role.startsWith("ROLE_") ? role.substring(5) : role;
            return user.roles().stream().anyMatch(candidate -> candidate.name().equals(normalized));
        }

        @Override
        public String getAuthType() {
            return "Bearer";
        }
    }
}
