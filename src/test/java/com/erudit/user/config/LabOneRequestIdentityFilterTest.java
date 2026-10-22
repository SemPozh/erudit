package com.erudit.user.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LabOneRequestIdentityFilterTest {
    private final LabOneRequestIdentityFilter filter = new LabOneRequestIdentityFilter();

    @Test
    void exposesUnverifiedRequestIdentityWithoutAuthorization() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(LabOneRequestIdentityFilter.USER_ID_HEADER, userId.toString());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        var wrapped = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(wrapped.getUserPrincipal().getName()).isEqualTo(userId.toString());
        assertThat(wrapped.isUserInRole("ADMIN")).isFalse();
        assertThat(wrapped.getAuthType()).isNull();
    }

    @Test
    void doesNotRejectMissingOrOpaqueIdentityHeader() throws Exception {
        for (String value : new String[]{null, "student-1"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            if (value != null) request.addHeader(LabOneRequestIdentityFilter.USER_ID_HEADER, value);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            String expected = value == null ? LabOneRequestIdentityFilter.DEFAULT_USER_ID : value;
            assertThat(((jakarta.servlet.http.HttpServletRequest) chain.getRequest())
                    .getUserPrincipal().getName()).isEqualTo(expected);
        }
    }
}
