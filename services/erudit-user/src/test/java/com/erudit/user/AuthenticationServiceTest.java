package com.erudit.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erudit.user.model.User;
import com.erudit.user.model.UserCredentials;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.AuthenticationService;
import com.erudit.user.service.InvalidCredentialsException;
import com.erudit.user.service.JwtTokenService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticationServiceTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtTokenService tokens = new JwtTokenService(
            "local-erudit-jwt-secret-at-least-32-bytes", "erudit-user", java.time.Duration.ofHours(1));
    private final AuthenticationService service = new AuthenticationService(repository, passwords, tokens);

    @Test void issuesSignedJwtForValidActiveUser() {
        var user = new User(UUID.randomUUID(), "user@example.com", "User", "AUTHOR", true, OffsetDateTime.now());
        when(repository.credentials("user@example.com")).thenReturn(Optional.of(new UserCredentials(user, "hash")));
        when(passwords.matches("password", "hash")).thenReturn(true);
        assertThat(service.login(" USER@EXAMPLE.COM ", "password").value()).contains(".");
    }

    @Test void rejectsUnknownBadPasswordAndBlockedUser() {
        assertThatThrownBy(() -> service.login("missing@example.com", "password")).isInstanceOf(InvalidCredentialsException.class);
        var user = new User(UUID.randomUUID(), "user@example.com", "User", "LEARNER", true, OffsetDateTime.now());
        when(repository.credentials("user@example.com")).thenReturn(Optional.of(new UserCredentials(user, "hash")));
        assertThatThrownBy(() -> service.login("user@example.com", "wrong-pass")).isInstanceOf(InvalidCredentialsException.class);
        when(passwords.matches("password", "hash")).thenReturn(true);
        when(repository.blocked(user.id())).thenReturn(true);
        assertThatThrownBy(() -> service.login("user@example.com", "password")).isInstanceOf(InvalidCredentialsException.class);
    }
}
