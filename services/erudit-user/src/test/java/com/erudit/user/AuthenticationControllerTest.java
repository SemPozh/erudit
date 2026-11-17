package com.erudit.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erudit.user.controller.AuthenticationController;
import com.erudit.user.openapi.model.LoginRequest;
import com.erudit.user.service.AuthenticationService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthenticationControllerTest {
    @Test void returnsBearerTokenWithoutCredentials() {
        var service = mock(AuthenticationService.class);
        when(service.login("user@example.com", "password")).thenReturn(new AuthenticationService.Token("jwt", Duration.ofHours(1)));
        var response = new AuthenticationController(service).login(new LoginRequest("user@example.com", "password"));
        assertThat(response.getBody().getAccessToken()).isEqualTo("jwt");
        assertThat(response.getBody().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().getExpiresIn()).isEqualTo(3600);
    }
}
