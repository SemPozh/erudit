package com.erudit.user.service;

import com.erudit.user.config.AuthProperties;
import com.erudit.user.domain.User;
import com.erudit.user.domain.UserRole;
import com.erudit.user.domain.UserStatus;
import com.erudit.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.security.MessageDigest;

@Service
public class JwtTokenService {
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final byte[] secret;
    private final AuthProperties properties;
    private final Clock clock;
    private final UserRepository userRepository;

    public JwtTokenService(AuthProperties properties, Clock clock, UserRepository userRepository) {
        this.properties = properties;
        this.clock = clock;
        this.secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.userRepository = userRepository;
        if (secret.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
    }



    public String issueAccessToken(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTtl());
        String payload = ("{\"iss\":\"erudit\",\"sub\":\"%s\",\"email\":\"%s\","
                + "\"roles\":[\"%s\"],\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"}")
                .formatted(user.getId(), escape(user.getEmail()), user.getRole().name(), issuedAt.getEpochSecond(),
                        expiresAt.getEpochSecond(), UUID.randomUUID());
        String content = encode(HEADER.getBytes(StandardCharsets.UTF_8)) + "."
                + encode(payload.getBytes(StandardCharsets.UTF_8));
        return content + "." + encode(sign(content));
    }

    public long accessTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }

    public AuthenticatedUser verifyAccessToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new InvalidAccessTokenException("Malformed access token");
            }
            String content = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(content), Base64.getUrlDecoder().decode(parts[2]))) {
                throw new InvalidAccessTokenException("Invalid access token signature");
            }
            JsonNode header = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode payload = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText())
                    || !"erudit".equals(payload.path("iss").asText())
                    || payload.path("exp").asLong(0) <= clock.instant().getEpochSecond()) {
                throw new InvalidAccessTokenException("Expired or invalid access token");
            }
            UUID id = UUID.fromString(payload.path("sub").asText());

            User user = userRepository.findById(id)
                    .orElseThrow(() -> new InvalidAccessTokenException("User not found"));

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new InvalidAccessTokenException("User account is not active");
            }
            String email = payload.path("email").asText();
            Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
            payload.path("roles").forEach(role -> roles.add(UserRole.valueOf(role.asText())));
            if (email.isBlank() || roles.isEmpty()) {
                throw new InvalidAccessTokenException("Access token has incomplete claims");
            }
            return new AuthenticatedUser(id, email, Set.copyOf(roles));
        } catch (InvalidAccessTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidAccessTokenException("Malformed access token", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign access token", exception);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
