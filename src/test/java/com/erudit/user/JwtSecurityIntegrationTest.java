package com.erudit.user;

import com.erudit.user.model.User;
import com.erudit.user.model.UserRole;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtSecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenService jwtTokenService;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void issueTokens() {
        userToken = token(UserRole.USER);
        adminToken = token(UserRole.ADMIN);
    }

    @Test
    void authenticatesProtectedRequestsAndRejectsMissingOrInvalidToken() throws Exception {
        String body = "{\"refreshToken\":\"%s\"}".formatted("x".repeat(32));
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/content")).andExpect(status().isOk());
    }

    @Test
    void restrictsAdministrativeRoutesByRole() throws Exception {
        String path = "/api/v1/admin/content/moderation";
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        int adminStatus = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andReturn().getResponse().getStatus();
        assertThat(adminStatus).isNotIn(401, 403);

        UUID missingContent = UUID.randomUUID();
        mvc.perform(delete("/api/v1/content/{id}", missingContent)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/content/{id}", missingContent)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    private String token(UserRole role) {
        User saved = userRepository.save(User.builder()
                .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .name(role.name())
                .role(role)
                .build());
        return jwtTokenService.issueAccessToken(saved);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
