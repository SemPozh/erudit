package com.erudit.user;

import com.erudit.media.model.MediaAsset;
import com.erudit.media.repository.MediaRepository;
import com.erudit.user.model.User;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private MediaRepository mediaRepository;
    @Autowired private JwtTokenService jwtTokenService;

    private User currentUser;
    private User otherUser;
    private String accessToken;

    @BeforeEach
    void createUsers() {
        currentUser = userRepository.save(User.builder()
                .email("profile-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").name("Initial Name").build());
        otherUser = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").name("Other User").build());
        accessToken = jwtTokenService.issueAccessToken(currentUser);
    }

    @Test
    void readsAndUpdatesOnlyProfileFromAccessToken() throws Exception {
        UUID avatarId = UUID.randomUUID();
        mediaRepository.save(new MediaAsset(avatarId, "avatars/" + avatarId, "avatar.png",
                "image/png", 128, Instant.now()));

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(currentUser.getId().toString()))
                .andExpect(jsonPath("$.data.email").value(currentUser.getEmail()))
                .andExpect(jsonPath("$.data.name").value("Initial Name"));

        mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Updated Name \",\"avatarId\":\"%s\"}".formatted(avatarId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.avatarUrl").value("/api/v1/media/" + avatarId));

        assertThat(userRepository.findById(otherUser.getId()).orElseThrow().getName()).isEqualTo("Other User");
    }

    @Test
    void requiresAuthenticationAndRejectsNonImageAvatar() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());

        UUID audioId = UUID.randomUUID();
        mediaRepository.save(new MediaAsset(audioId, "audio/" + audioId, "sound.mp3",
                "audio/mpeg", 128, Instant.now()));
        mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarId\":\"%s\"}".formatted(audioId)))
                .andExpect(status().isBadRequest());
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }
}
