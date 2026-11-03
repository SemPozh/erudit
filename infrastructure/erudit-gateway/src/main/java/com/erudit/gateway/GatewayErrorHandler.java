package com.erudit.gateway;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {
    private final ObjectMapper mapper;

    public GatewayErrorHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) { return Mono.error(error); }
        int status = error instanceof ResponseStatusException response ? response.getStatusCode().value() : 500;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof TimeoutException || cause instanceof io.netty.handler.timeout.TimeoutException
                    || cause instanceof reactor.netty.http.client.PrematureCloseException) {
                status = 503;
                break;
            }
        }
        String code = switch (status) {
            case 404 -> "NOT_FOUND";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 503, 504 -> "SERVICE_UNAVAILABLE";
            default -> status < 500 ? "INVALID_REQUEST" : "INTERNAL_ERROR";
        };
        if (status == 504) { status = 503; }
        var httpStatus = HttpStatus.resolve(status);
        String message = httpStatus == null ? "Request failed" : httpStatus.getReasonPhrase();
        byte[] body = mapper.writeValueAsBytes(Map.of("error", Map.of("code", code, "message", message)));
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
