package com.erudit.observability;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class WebCompatibilityTest {
    @ParameterizedTest
    @EnumSource(value = WebApplicationType.class, names = {"SERVLET", "REACTIVE"})
    void errorsContextMetricsAndTracingAreCompatible(WebApplicationType type, CapturedOutput output) throws Exception {
        Class<?> configuration = type == WebApplicationType.SERVLET ? ServletFixture.class : ReactiveFixture.class;
        try (var context = new SpringApplicationBuilder(configuration).web(type).run(
                "--server.port=0", "--spring.config.name=isolated-test", "--logging.level.root=ERROR",
                "--management.tracing.sampling.probability=1"); var client = HttpClient.newHttpClient()) {
            int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
            assertThat(context.getEnvironment().getProperty("logging.structured.format.console")).isEqualTo("ecs");
            var response = client.send(request(port, "/context").header("X-Request-ID", "context-123")
                    .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("X-Request-ID")).contains("context-123");
            var payload = JsonMapper.builder().build().readTree(response.body());
            assertThat(payload.get("mdc").asText()).isEqualTo("context-123");
            assertThat(payload.get("trace").asText()).isEqualTo("0123456789abcdef0123456789abcdef");
            if (type == WebApplicationType.REACTIVE) { assertThat(payload.get("context").asText()).isEqualTo("context-123"); }
            String logLine = output.getAll().lines().filter(line -> line.startsWith("{")
                    && line.contains("\"requestId\":\"context-123\"")).findFirst().orElseThrow();
            var log = JsonMapper.builder().build().readTree(logLine);
            assertThat(log.get("log").get("level").asText()).isEqualTo("ERROR");
            assertThat(log.get("traceId").asText()).isEqualTo("0123456789abcdef0123456789abcdef");
            var echo = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            echo.createContext("/", exchange -> {
                byte[] bytes = String.valueOf(exchange.getRequestHeaders().getFirst("traceparent"))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            echo.start();
            try {
                var tracer = context.getBean(Tracer.class);
                var span = tracer.nextSpan().name("outbound-test").start();
                try (var scope = tracer.withSpan(span)) {
                    String propagated = context.getBean(RestClient.Builder.class).build().get()
                            .uri("http://127.0.0.1:" + echo.getAddress().getPort()).retrieve().body(String.class);
                    assertThat(propagated).contains(span.context().traceId());
                } finally { span.end(); }
            } finally { echo.stop(0); }

            assertError(client, request(port, "/validate").POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"\"}"))
                    .build(), 400, "VALIDATION_ERROR");
            assertError(client, request(port, "/validate").POST(HttpRequest.BodyPublishers.ofString("{"))
                    .build(), 400, "MALFORMED_JSON");
            assertError(client, request(port, "/missing").GET().build(), 404, "NOT_FOUND");
            assertError(client, request(port, "/boom").GET().build(), 500, "INTERNAL_ERROR");
            assertError(client, request(port, "/status").GET().build(), 400, "VALIDATION_ERROR");
            var invalidId = client.send(request(port, "/context").header("X-Request-ID", "invalid id").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(invalidId.headers().firstValue("X-Request-ID").orElseThrow()).matches("[a-f0-9-]{36}");
            for (String path : new String[]{"/actuator/health/liveness", "/actuator/health/readiness", "/actuator/prometheus"}) {
                var health = client.send(request(port, path).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(health.statusCode()).as(path).isEqualTo(200);
                assertThat(health.body()).contains(path.endsWith("prometheus") ? "jvm_memory" : "UP");
            }
        }
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    private HttpRequest.Builder request(int port, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
    }
    private void assertError(HttpClient client, HttpRequest request, int status, String code) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("X-Request-ID")).isPresent();
        var json = JsonMapper.builder().build().readTree(response.body());
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get("error").get("code").asText()).isEqualTo(code);
        assertThat(json.get("error").get("message").asText()).isNotBlank().doesNotContain("private-details");
    }

    record Payload(@NotBlank String name) { }
    @RestController
    static class Errors {
        @PostMapping("/validate") Payload validate(@Valid @RequestBody Payload payload) { return payload; }
        @GetMapping("/boom") void boom() { throw new IllegalStateException("private-details"); }
        @GetMapping("/status") void status() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "private-details"); }
    }
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration
    static class ServletFixture {
        @Bean Errors errors() { return new Errors(); }
        @Bean ServletContextEndpoint endpoint(Tracer tracer) { return new ServletContextEndpoint(tracer); }
    }
    @RestController
    record ServletContextEndpoint(Tracer tracer) {
        @GetMapping("/context") Map<String, String> context() {
            LoggerFactory.getLogger("context-test").error("servlet context");
            return Map.of("mdc", String.valueOf(MDC.get(RequestIds.MDC_KEY)),
                    "trace", tracer.currentSpan().context().traceId());
        }
    }
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration
    static class ReactiveFixture {
        @Bean Errors errors() { return new Errors(); }
        @Bean ReactiveContextEndpoint endpoint(Tracer tracer) { return new ReactiveContextEndpoint(tracer); }
    }
    @RestController
    record ReactiveContextEndpoint(Tracer tracer) {
        @GetMapping("/context") Mono<Map<String, String>> context() {
            return Mono.deferContextual(context -> Mono.just(context.get(RequestIds.HEADER).toString()))
                    .publishOn(Schedulers.parallel()).map(id -> {
                        LoggerFactory.getLogger("context-test").error("reactive context");
                        return Map.of("context", id, "mdc", String.valueOf(MDC.get(RequestIds.MDC_KEY)),
                                "trace", tracer.currentSpan().context().traceId());
                    });
        }
    }
}
