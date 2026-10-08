package com.erudit.user;

import com.erudit.user.model.User;
import com.erudit.user.service.EmailSender;
import com.erudit.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailSender emailSender;

    private String email;

    @BeforeEach
    void createUser() {
        email = "reset-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.builder().email(email)
                .passwordHash(passwordEncoder.encode("old-password"))
                .name("Reset Student").build());
    }

    @Test
    void resetsPasswordOnceAndRevokesExistingRefreshTokens() throws Exception {
        String loginJson = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("old-password")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oldRefresh = new com.fasterxml.jackson.databind.ObjectMapper().readTree(loginJson)
                .at("/data/refreshToken").asText();

        mvc.perform(post("/api/v1/auth/password/reset-request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isNoContent());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendPasswordResetEmail(anyString(), anyString(), link.capture());
        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        String resetBody = "{\"token\":\"%s\",\"newPassword\":\"new-password\"}".formatted(rawToken);
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("old-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("new-password")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(oldRefresh)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hidesUnknownEmailsAndValidatesNewPassword() throws Exception {
        mvc.perform(post("/api/v1/auth/password/reset-request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isNoContent());
        verifyNoInteractions(emailSender);

        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"short\"}"
                                .formatted("x".repeat(43))))
                .andExpect(status().isBadRequest());
    }

    private String loginBody(String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}
