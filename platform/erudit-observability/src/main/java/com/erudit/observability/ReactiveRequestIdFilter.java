package com.erudit.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


public class ReactiveRequestIdFilter implements WebFilter, Ordered {
    public static final String HEADER = "X-Request-ID";
    private static final Logger LOG = LoggerFactory.getLogger(ReactiveRequestIdFilter.class);

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
        String id = RequestIds.normalize(supplied);
        long started = System.nanoTime();
        var request = exchange.getRequest().mutate().headers(headers -> headers.set(HEADER, id)).build();
        exchange.getAttributes().put(HEADER, id);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, id);
            LOG.info("requestId={} method={} path={} status={} durationMs={}", id,
                    request.getMethod(), request.getPath().value(), exchange.getResponse().getStatusCode(),
                    (System.nanoTime() - started) / 1_000_000);
            return Mono.empty();
        });
        return chain.filter(exchange.mutate().request(request).build()).contextWrite(context -> context.put(HEADER, id));
    }
}

