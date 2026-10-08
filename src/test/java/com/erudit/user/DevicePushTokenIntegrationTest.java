package com.erudit.user;

import com.erudit.user.model.DevicePushToken;
import com.erudit.user.model.PushPlatform;
import com.erudit.user.model.User;
import com.erudit.user.repository.DevicePushTokenRepository;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevicePushTokenIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private DevicePushTokenRepository tokenRepository;
    @Autowired private JwtTokenService jwtTokenService;

    private User user;
    private User otherUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        user = createUser("push");
        otherUser = createUser("other-push");
        accessToken = jwtTokenService.issueAccessToken(user);
    }

    @Test
    void registersIdempotentlyAndRevokesOnlyOwnedToken() throws Exception {
        register("device-token", "ANDROID").andExpect(status().isNoContent());
        register("device-token", "IOS").andExpect(status().isNoContent());

        DevicePushToken saved = tokenRepository.findByToken("device-token").orElseThrow();
        assertThat(tokenRepository.count()).isOne();
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getPlatform()).isEqualTo(PushPlatform.IOS);
        assertThat(saved.isActive()).isTrue();

        mvc.perform(delete("/api/v1/users/me/push-tokens/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertThat(tokenRepository.findById(saved.getId()).orElseThrow().isActive()).isFalse();

        register("device-token", "IOS").andExpect(status().isNoContent());
        assertThat(tokenRepository.findById(saved.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void cannotRevokeAnotherUsersTokenAndRejectsInvalidInput() throws Exception {
        DevicePushToken foreign = tokenRepository.save(DevicePushToken.builder()
                .id(UUID.randomUUID()).userId(otherUser.getId()).token("foreign-token")
                .platform(PushPlatform.ANDROID).active(true)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        mvc.perform(delete("/api/v1/users/me/push-tokens/{id}", foreign.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound());
        assertThat(tokenRepository.findById(foreign.getId()).orElseThrow().isActive()).isTrue();

        register("   ", "ANDROID").andExpect(status().isBadRequest());
        register("token", "DESKTOP").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users/me/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions register(String token, String platform) throws Exception {
        return mvc.perform(post("/api/v1/users/me/push-tokens")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"platform\":\"%s\"}".formatted(token, platform)));
    }

    private User createUser(String prefix) {
        return userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").name(prefix).build());
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }
}
