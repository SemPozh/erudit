package com.erudit.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class RequestBodyLimitFilter implements WebFilter, Ordered {
    private final int maxBytes;

    public RequestBodyLimitFilter(@Value("${erudit.gateway.max-body-bytes:54525952}") int maxBytes) {
        if (maxBytes <= 0) { throw new IllegalArgumentException("max-body-bytes must be positive"); }
        this.maxBytes = maxBytes;
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getHeaders().getContentLength() > maxBytes) {
            return Mono.error(tooLarge());
        }
        // Validate even chunked bodies before forwarding, so oversized writes never reach a service.
        return DataBufferUtils.join(exchange.getRequest().getBody(), maxBytes)
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .defaultIfEmpty(new byte[0])
                .onErrorMap(DataBufferLimitException.class, error -> tooLarge())
                .flatMap(bytes -> chain.filter(exchange.mutate().request(new ServerHttpRequestDecorator(exchange.getRequest()) {
                    @Override
                    public Flux<DataBuffer> getBody() {
                        return bytes.length == 0 ? Flux.empty()
                                : Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));
                    }
                }).build()));
    }

    private ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Request body exceeds the gateway limit");
    }
}
