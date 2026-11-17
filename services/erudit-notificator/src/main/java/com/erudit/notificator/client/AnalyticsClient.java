package com.erudit.notificator.client;

import com.erudit.integration.InternalContracts.AnalyticsEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AnalyticsClient {
    private final WebClient client;

    public AnalyticsClient(@Value("${erudit.analytics.url:http://event-receiver:8085}") String baseUrl) {
        this.client = WebClient.builder().baseUrl(baseUrl).build();
    }

    public Mono<Void> publish(AnalyticsEvent event) {
        return client.post().uri("/api/v1/events").bodyValue(event).retrieve().toBodilessEntity().then();
    }
}