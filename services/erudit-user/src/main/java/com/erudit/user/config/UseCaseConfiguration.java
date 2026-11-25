package com.erudit.user.config;

import com.erudit.user.application.*;
import com.erudit.user.application.port.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import java.util.Properties;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean Passwords passwords(PasswordEncoder encoder) {
        return new Passwords() {
            public String encode(CharSequence password) { return encoder.encode(password); }
            public boolean matches(CharSequence password, String encoded) { return encoder.matches(password, encoded); }
        };
    }
    @Bean UserService userService(UserRepository users, Passwords passwords, PlatformTransactionManager transactions) {
        return transactional(new UserService(users, passwords), transactions, "register", "verify", "update", "delete", "settings");
    }
    @Bean AuthenticationService authenticationService(UserRepository users, Passwords passwords, TokenIssuer tokens) {
        return new AuthenticationService(users, passwords, tokens);
    }
    @Bean SocialService socialService(SocialRepository social, PlatformTransactionManager transactions) {
        return transactional(new SocialService(social), transactions, "request", "decide", "remove", "device", "revoke");
    }
    @Bean InternalUserService internalUserService(UserRepository users, SocialRepository social, PlatformTransactionManager transactions) {
        return transactional(new InternalUserService(users, social), transactions, "block");
    }
    private static <T> T transactional(T target, PlatformTransactionManager transactions, String... writes) {
        Properties attributes = new Properties();
        attributes.setProperty("*", "PROPAGATION_REQUIRED,readOnly");
        for (String method : writes) attributes.setProperty(method, "PROPAGATION_REQUIRED");
        TransactionProxyFactoryBean proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(target);
        proxy.setProxyTargetClass(true);
        proxy.setTransactionManager(transactions);
        proxy.setTransactionAttributes(attributes);
        proxy.afterPropertiesSet();
        return (T) proxy.getObject();
    }
}
