package com.erudit.user.service;

import com.erudit.user.repository.UserRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;

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
