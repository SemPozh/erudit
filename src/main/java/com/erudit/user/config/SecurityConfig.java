package com.erudit.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/content",
                                "/api/v1/content/categories",
                                "/api/v1/content/formats",
                                "/api/v1/subscriptions/plans"
                        ).permitAll()
                        .requestMatchers(
                                "/api/v1/media/**",
                                "/api/v1/content/*/quiz-cards"
                        ).permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/hello",
                                "/openapi.yaml",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
