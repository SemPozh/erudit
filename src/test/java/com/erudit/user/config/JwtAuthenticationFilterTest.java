package com.erudit.user.config;

import com.erudit.user.model.UserRole;
import com.erudit.user.service.AuthenticatedUser;
import com.erudit.user.service.InvalidAccessTokenException;
import com.erudit.user.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    @Test
    void exposesVerifiedIdentityAsServletPrincipalAndRole() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtTokenService tokens = mock(JwtTokenService.class);
        when(tokens.verifyAccessToken("valid-token"))
                .thenReturn(new AuthenticatedUser(userId, "admin@example.com", Set.of(UserRole.ADMIN)));
        JwtAuthenticationFilter filter = filter(tokens);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        var authenticated = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(authenticated.getUserPrincipal().getName()).isEqualTo(userId.toString());
        assertThat(authenticated.isUserInRole("ADMIN")).isTrue();
        assertThat(authenticated.isUserInRole("USER")).isFalse();
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        JwtTokenService tokens = mock(JwtTokenService.class);
        when(tokens.verifyAccessToken("invalid"))
                .thenThrow(new InvalidAccessTokenException("invalid"));
        JwtAuthenticationFilter filter = filter(tokens);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    @SuppressWarnings("unchecked")
    private static JwtAuthenticationFilter filter(JwtTokenService tokens) {
        ObjectProvider<JwtTokenService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tokens);
        return new JwtAuthenticationFilter(provider);
    }
}
