package com.erudit.observability;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class ObservabilityDefaults implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource("eruditObservabilityDefaults", Map.of(
                "logging.structured.format.console", "ecs",
                "management.endpoints.web.exposure.include", "health,info,prometheus",
                "management.endpoint.health.probes.enabled", "true",
                "management.tracing.propagation.type", "w3c",
                "management.tracing.export.zipkin.enabled", "${ZIPKIN_ENABLED:false}",
                "management.tracing.export.zipkin.endpoint", "${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}",
                "management.tracing.sampling.probability", "${TRACING_SAMPLING_PROBABILITY:0.1}",
                "spring.reactor.context-propagation", "auto")));
    }
}
