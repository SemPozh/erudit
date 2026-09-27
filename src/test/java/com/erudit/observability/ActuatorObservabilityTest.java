package com.erudit.observability;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorObservabilityTest {
    @Autowired private MockMvc mvc;
    @Autowired private PrometheusMeterRegistry prometheus;
    @Autowired private Tracer tracer;

    @Test
    void exposesHealthMetricsPrometheusAndCorrelationHeader() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_info")));
        mvc.perform(get("/hello").header(RequestCorrelationFilter.HEADER, "integration-42"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(RequestCorrelationFilter.HEADER, "integration-42"));

        assertThat(prometheus.scrape()).contains("jvm_info");
        Span span = tracer.nextSpan().name("observability-test").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            assertThat(tracer.currentSpan().context().traceId()).isNotBlank();
            assertThat(tracer.currentSpan().context().spanId()).isNotBlank();
        } finally {
            span.end();
        }
    }
}
