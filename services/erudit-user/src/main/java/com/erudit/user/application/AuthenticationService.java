package com.erudit.user.application;

import com.erudit.user.application.port.UserRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import com.erudit.user.application.port.Passwords;

@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository repository;
    private final Passwords passwordEncoder;
    private final com.erudit.user.application.port.TokenIssuer tokens;

    public Token login(String email, String password) {
        var credentials = repository.credentials(email.trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, credentials.passwordHash()) || repository.blocked(credentials.user().id())) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(credentials.user());
    }

    public record Token(String value, Duration expiresIn) { }
}
