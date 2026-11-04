package com.erudit.eventreceiver.controller;

import com.erudit.eventreceiver.openapi.api.AnalyticsApi;
import com.erudit.eventreceiver.openapi.model.AnalyticsResponse;
import com.erudit.eventreceiver.service.ClickHouseEventStore;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class AnalyticsController implements AnalyticsApi {
    private final ClickHouseEventStore store;
    @Override
    public Mono<ResponseEntity<AnalyticsResponse>> getAnalyticsMetric(String metric, OffsetDateTime from,
                                                                       OffsetDateTime to, ServerWebExchange exchange) {
        if (!from.isBefore(to)) return Mono.error(new IllegalArgumentException("from must be before to"));
        return store.aggregate(metric, from, to).map(value -> ResponseEntity.ok(
                new AnalyticsResponse(metric, value.count(), value.users(), value.value())));
    }
}
