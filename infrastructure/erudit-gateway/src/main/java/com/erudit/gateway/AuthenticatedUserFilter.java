package com.erudit.gateway;

import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class AuthenticatedUserFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal().flatMap(principal -> forward(exchange, chain,
                principal instanceof JwtAuthenticationToken jwt ? jwt : null))
                .switchIfEmpty(Mono.defer(() -> forward(exchange, chain, null)));
    }

    private Mono<Void> forward(ServerWebExchange exchange, WebFilterChain chain, JwtAuthenticationToken jwt) {
        var request = exchange.getRequest().mutate().headers(value -> {
            value.remove("X-User-Id");
            value.remove("X-User-Roles");
            if (jwt != null) {
                value.set("X-User-Id", jwt.getToken().getSubject());
                value.set("X-User-Roles", jwt.getAuthorities().stream()
                        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                        .collect(Collectors.joining(",")));
            }
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
