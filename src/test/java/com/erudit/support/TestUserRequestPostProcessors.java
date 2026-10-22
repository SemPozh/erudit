package com.erudit.support;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.security.Principal;
import java.util.Arrays;

import static com.erudit.user.config.LabOneRequestIdentityFilter.USER_ID_HEADER;

public final class TestUserRequestPostProcessors {
    private TestUserRequestPostProcessors() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static UserRequestPostProcessor user(String username) {
        return new UserRequestPostProcessor(username);
    }

    public static final class UserRequestPostProcessor implements RequestPostProcessor {
        private final Principal principal;
        private String[] roles = {"USER"};

        private UserRequestPostProcessor(String username) {
            this.principal = () -> username;
        }

        public UserRequestPostProcessor roles(String... roles) {
            this.roles = Arrays.copyOf(roles, roles.length);
            return this;
        }

        @Override
        public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
            request.addHeader(USER_ID_HEADER, principal.getName());
            request.setUserPrincipal(principal);
            Arrays.stream(roles).forEach(request::addUserRole);
            return request;
        }
    }
}
