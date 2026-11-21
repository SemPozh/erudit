package com.erudit.user.service;

import com.erudit.user.model.User;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final NimbusJwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;

    public JwtTokenService(@Value("${erudit.jwt.secret}") String secret,
                           @Value("${erudit.jwt.issuer:erudit-user}") String issuer,
                           @Value("${erudit.jwt.ttl:1h}") Duration ttl) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).build();
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public AuthenticationService.Token issue(User user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(now).expiresAt(now.plus(ttl))
                .subject(user.id().toString()).claim("email", user.email()).claim("roles", List.of(user.role())).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new AuthenticationService.Token(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), ttl);
    }
}
