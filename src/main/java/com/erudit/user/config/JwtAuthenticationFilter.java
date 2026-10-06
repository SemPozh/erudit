package com.erudit.user.config;

import com.erudit.user.service.AuthenticatedUser;
import com.erudit.user.service.InvalidAccessTokenException;
import com.erudit.user.service.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
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
            var authorities = user.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
            var authentication = new UsernamePasswordAuthenticationToken(user.id().toString(), null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (InvalidAccessTokenException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of("error", Map.of(
                    "code", "UNAUTHORIZED", "message", "Invalid or expired access token")));
        }
    }
}
