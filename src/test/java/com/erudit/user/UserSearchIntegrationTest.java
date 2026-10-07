package com.erudit.user;

import com.erudit.user.domain.User;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.JwtTokenService;
import com.erudit.user.service.UserPreferencesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSearchIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferencesService preferencesService;
    @Autowired private JwtTokenService jwtTokenService;

    private String bearer;
    private String marker;

    @BeforeEach
    void setUp() {
        marker = UUID.randomUUID().toString().substring(0, 8);
        User viewer = create("Viewer " + marker, "viewer-" + marker + "@example.com");
        bearer = "Bearer " + jwtTokenService.issueAccessToken(viewer);
    }

    @Test
    void searchesNameAndEmailWhileHidingPrivateProfiles() throws Exception {
        User byName = create("Alice " + marker, "person-a-" + marker + "@example.com");
        User byEmail = create("Bob " + marker, "alice-mail-" + marker + "@example.com");
        User hidden = create("Alice Hidden " + marker, "hidden-" + marker + "@example.com");
        preferencesService.update(hidden.getId(), "ru", "UTC", Set.of(), 15,
                false, true, false);

        mvc.perform(get("/api/v1/users/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .param("query", "ALICE").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == '%s')]", byName.getId()).exists())
                .andExpect(jsonPath("$.data[?(@.id == '%s')]", byEmail.getId()).exists())
                .andExpect(jsonPath("$.data[?(@.id == '%s')]", hidden.getId()).doesNotExist());
    }

    @Test
    void validatesPaginationAndRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/search").header(HttpHeaders.AUTHORIZATION, bearer)
                        .param("query", " "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/search").header(HttpHeaders.AUTHORIZATION, bearer)
                        .param("query", marker).param("size", "51"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/search").param("query", marker))
                .andExpect(status().isUnauthorized());
    }

    private User create(String name, String email) {
        return userRepository.save(User.builder().email(email).passwordHash("hash").name(name).build());
    }
}
