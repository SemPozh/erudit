package com.erudit.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/** Routing boundary, not authorization: only documented public operations may reach owners. */
@Component
public class PublicApiFilter implements WebFilter {
    private final PublicApiCatalog catalog;
    public PublicApiFilter(PublicApiCatalog catalog){this.catalog=catalog;}
    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        String path=exchange.getRequest().getPath().value();
        if(path.startsWith("/api/v1/")&&!catalog.supports(exchange.getRequest().getMethod().name(),path))
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        return chain.filter(exchange);
    }
}
