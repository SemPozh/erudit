package com.erudit.observability;

import jakarta.validation.ConstraintViolationException;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

public class ReactiveTechnicalErrors implements ErrorWebExceptionHandler, Ordered {
    private final ObjectMapper mapper;
    public ReactiveTechnicalErrors(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public int getOrder() { return -2; }
    @Override public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) { return Mono.error(error); }
        int status = error instanceof ConstraintViolationException ? 400
                : error instanceof ErrorResponse response ? response.getStatusCode().value() : 500;
        boolean malformed = error instanceof ServerWebInputException && !(error instanceof WebExchangeBindException);
        byte[] body = mapper.writeValueAsBytes(TechnicalError.of(status, malformed));
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
