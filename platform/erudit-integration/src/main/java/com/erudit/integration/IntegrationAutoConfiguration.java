package com.erudit.integration;

import feign.RequestInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.cloud.openfeign.EnableFeignClients;

@AutoConfiguration
@EnableFeignClients(clients = {InternalClients.UserClient.class, InternalClients.ModerationClient.class,
        InternalClients.NotificationClient.class, InternalClients.EntitlementClient.class,
        InternalClients.ContentClient.class, InternalClients.AnalyticsClient.class})
public class IntegrationAutoConfiguration {
    public static final String REQUEST_ID = "X-Request-Id";

    @Bean
    RequestInterceptor requestIdInterceptor() {
        return template -> {
            String requestId = RequestContext.requestId();
            if (requestId != null) template.header(REQUEST_ID, requestId);
        };
    }

    @Bean
    CircuitBreakerConfigCustomizer internalCircuitBreakerPolicy() {
        return CircuitBreakerConfigCustomizer.of("internal", builder -> builder
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2));
    }

    @Bean
    TaskDecorator requestContextTaskDecorator() {
        return runnable -> {
            String requestId = RequestContext.requestId();
            return () -> RequestContext.withRequestId(requestId, runnable);
        };
    }
}
