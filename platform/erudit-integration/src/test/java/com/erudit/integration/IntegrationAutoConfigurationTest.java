package com.erudit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestTemplate;
import java.util.concurrent.atomic.AtomicReference;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

class IntegrationAutoConfigurationTest {
    private final IntegrationAutoConfiguration configuration = new IntegrationAutoConfiguration();

    @Test
    void propagatesRequestIdAndClearsContext() {
        var template = new RequestTemplate();
        RequestContext.withRequestId("request-1", () -> configuration.requestIdInterceptor().apply(template));
        assertThat(template.headers().get(IntegrationAutoConfiguration.REQUEST_ID)).containsExactly("request-1");
        assertThat(RequestContext.requestId()).isNull();
    }

    @Test
    void taskDecoratorPropagatesCapturedContext() {
        var value = new AtomicReference<String>();
        RequestContext.withRequestId("request-2", () -> configuration.requestContextTaskDecorator()
                .decorate(() -> value.set(RequestContext.requestId())).run());
        assertThat(value).hasValue("request-2");
        assertThat(configuration.internalCircuitBreakerPolicy()).isNotNull();
    }

    @Test
    void circuitBreakerUsesFallbackAndSupportsOpenAndHalfOpenStates() {
        var config = CircuitBreakerConfig.custom().slidingWindowSize(2).minimumNumberOfCalls(2)
                .failureRateThreshold(50).permittedNumberOfCallsInHalfOpenState(1).build();
        var breaker = CircuitBreaker.of("test", config);
        assertThat(ResilientExecutor.execute(breaker, () -> "closed", () -> "fallback")).isEqualTo("closed");
        ResilientExecutor.execute(breaker, () -> { throw new IllegalStateException(); }, () -> "fallback");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(ResilientExecutor.execute(breaker, () -> "blocked", () -> "fallback")).isEqualTo("fallback");
        breaker.transitionToHalfOpenState();
        assertThat(ResilientExecutor.execute(breaker, () -> "recovered", () -> "fallback")).isEqualTo("recovered");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
