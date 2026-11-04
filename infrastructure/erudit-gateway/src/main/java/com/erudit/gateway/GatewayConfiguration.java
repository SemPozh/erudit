package com.erudit.gateway;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class GatewayConfiguration {
    @Bean
    RouteLocator publicRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("content-moderation", r -> r.path("/api/v1/admin/content/**").uri("lb://erudit-content"))
                .route("admin", r -> r.path("/api/v1/admin/**").and().not(p -> p.path("/api/v1/admin/content/**")).uri("lb://erudit-admin"))
                .route("user", r -> r.path("/api/v1/auth/**", "/api/v1/users/**", "/api/v1/friends/**")
                        .uri("lb://erudit-user"))
                .route("content", r -> r.path("/api/v1/content/**", "/api/v1/media/**")
                        .uri("lb://erudit-content"))
                .route("quiz", r -> r.path("/api/v1/assessment/**", "/api/v1/program/**", "/api/v1/quiz/**",
                        "/api/v1/competition/**", "/api/v1/rating/**").uri("lb://erudit-quiz"))
                .route("payment", r -> r.path("/api/v1/subscriptions/**", "/api/v1/payments/**")
                        .uri("lb://erudit-payment"))
                .route("notification", r -> r.path("/api/v1/notifications/**").uri("lb://erudit-notificator"))
                .route("event", r -> r.path("/api/v1/events/**", "/api/v1/analytics/**")
                        .uri("lb://erudit-event-receiver"))
                .build();
    }

    @Bean
    CorsWebFilter corsWebFilter(@Value("${erudit.gateway.allowed-origins:http://localhost:3000,http://localhost:5173}")
                               List<String> origins) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        cors.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Request-ID", "X-User-Id"));
        cors.setExposedHeaders(List.of("X-Request-ID", "X-Total-Count", "Location", "X-Has-More", "X-Next-Page"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", cors);
        return new CorsWebFilter(source);
    }
}
