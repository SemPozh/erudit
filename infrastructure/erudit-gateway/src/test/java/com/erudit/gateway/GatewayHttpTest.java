package com.erudit.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "springdoc.swagger-ui.config-url=/openapi/swagger-config",
        "springdoc.swagger-ui.disable-swagger-default-url=true",
        "spring.config.name=isolated-test", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "erudit.gateway.max-body-bytes=64", "spring.cloud.loadbalancer.cache.enabled=false",
        "spring.cloud.gateway.server.webflux.httpclient.connect-timeout=300",
        "spring.cloud.gateway.server.webflux.httpclient.response-timeout=1s",
        "erudit.jwt.secret=local-erudit-jwt-secret-at-least-32-bytes", "erudit.jwt.issuer=erudit-user"
})
class GatewayHttpTest {
    private static final HttpServer UPSTREAM;
    private static final int DEAD_PORT;
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    static {
        try {
            UPSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            UPSTREAM.createContext("/", exchange -> {
                REQUESTS.incrementAndGet();
                String body = exchange.getRequestMethod() + " " + exchange.getRequestURI()
                        + "|user=" + exchange.getRequestHeaders().getFirst("X-User-Id")
                        + "|id=" + exchange.getRequestHeaders().getFirst("X-Request-ID")
                        + "|auth=" + exchange.getRequestHeaders().getFirst("Authorization")
                        + "|body=" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.getResponseHeaders().add("X-Request-ID", "upstream-must-not-overwrite");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            UPSTREAM.start();
            try (var socket = new ServerSocket(0)) { DEAD_PORT = socket.getLocalPort(); }
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }

    @Value("${local.server.port}") int port;
    @Autowired RouteLocator routes;

    @DynamicPropertySource
    static void discovery(DynamicPropertyRegistry registry) {
        for (String service : new String[]{"admin", "user", "content", "quiz", "notificator"}) {
            registry.add("spring.cloud.discovery.client.simple.instances.erudit-" + service + "[0].uri",
                    () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
        }
        registry.add("spring.cloud.discovery.client.simple.instances.erudit-payment[0].uri",
                () -> "http://127.0.0.1:" + DEAD_PORT);
    }

    @AfterAll static void close() { UPSTREAM.stop(0); }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/audit", "/api/v1/users/me",
            "/api/v1/friends", "/api/v1/content", "/api/v1/media/123", "/api/v1/quizzes/daily",
            "/api/v1/learning-program", "/api/v1/assessments/questions", "/api/v1/competitions/123/leaderboard", "/api/v1/ratings",
            "/api/v1/notifications", "/api/v1/admin/content/moderation", "/api/v1/admin/content/reports"})
    void routesWithAuthorizationAndDerivesTrustedContext(String path) throws Exception {
        var response = send(request(path + "?page=2").header("X-User-Id", "spoofed")
                .header("X-Request-ID", "trace-123").GET().build());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(path + "?page=2", "user=00000000-0000-0000-0000-000000000001", "id=trace-123", "auth=Bearer ");
        assertThat(response.headers().allValues("X-Request-ID")).containsExactly("trace-123");
    }

    @Test void generatesIdAndForwardsBody() throws Exception {
        var response = send(request("/api/v1/content").POST(HttpRequest.BodyPublishers.ofString("hello")).build());
        String id = response.headers().firstValue("X-Request-ID").orElseThrow();
        assertThat(id).matches("[a-f0-9-]{36}");
        assertThat(response.body()).contains("id=" + id, "body=hello", "user=00000000-0000-0000-0000-000000000001");
    }

    @Test void replacesInvalidRequestId() throws Exception {
        var response = send(request("/api/v1/content").header("X-Request-ID", "invalid id").GET().build());
        assertThat(response.headers().firstValue("X-Request-ID").orElseThrow()).matches("[a-f0-9-]{36}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/missing", "/api/v1/missing", "/internal/v1/users", "/eureka/apps",
            "/erudit-config-server/default", "/erudit-user/api/v1/users", "/actuator/gateway/routes"})
    void exposesNoPrivateOrDiscoveryRoutes(String path) throws Exception {
        int before = REQUESTS.get();
        var response = send(request(path).GET().build());
        assertError(response, 404, "NOT_FOUND");
        assertThat(REQUESTS.get()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/analytics/sessions", "/api/v1/subscriptions/plans"})
    void unavailableOrUnregisteredServiceReturns503(String path) throws Exception {
        assertError(send(request(path).GET().build()), 503, "SERVICE_UNAVAILABLE");
    }

    @Test void rejectsKnownLengthAndChunkedOversizeBeforeForwarding() throws Exception {
        byte[] body = new byte[65];
        int before = REQUESTS.get();
        assertError(send(request("/api/v1/content").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()),
                413, "PAYLOAD_TOO_LARGE");
        assertError(send(request("/api/v1/content").POST(HttpRequest.BodyPublishers.ofInputStream(
                () -> new ByteArrayInputStream(body))).build()), 413, "PAYLOAD_TOO_LARGE");
        assertThat(REQUESTS.get()).isEqualTo(before);
        assertThat(send(request("/api/v1/content").POST(HttpRequest.BodyPublishers.ofByteArray(new byte[64]))
                .build()).statusCode()).isEqualTo(200);
    }

    @Test void corsAllowsConfiguredOriginsAndRejectsOthers() throws Exception {
        var allowed = send(request("/api/v1/content").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,X-Request-ID,Content-Type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:3000");
        var denied = send(request("/api/v1/content").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
        assertThat(denied.statusCode()).isEqualTo(403);
    }

    @Test void allExistingPublicContractPathsHaveExactlyOneDiscoveryRoute() throws Exception {
        var configured = routes.getRoutes().collectList().block();
        assertThat(configured).hasSize(7).allSatisfy(route -> assertThat(route.getUri().getScheme()).isEqualTo("lb"));
        try (var contracts = Files.walk(Path.of(System.getProperty("service.contracts.root")))) {
            for (Path contract : contracts.filter(path -> path.endsWith("src/main/resources/static/openapi.yaml")).toList()) {
                for (String line : Files.readAllLines(contract)) {
                    if (!line.startsWith("  /api/v1/")) { continue; }
                    String path = line.trim().replaceAll("\\{[^}]+}", "123").replaceAll(":$", "");
                    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
                    var matching = configured.stream().filter(route -> Boolean.TRUE.equals(
                            reactor.core.publisher.Mono.from(route.getPredicate().apply(exchange)).block())).toList();
                    assertThat(matching).as(contract + ": " + path).hasSize(1);
                    if (path.startsWith("/api/v1/admin/content/")) {
                        assertThat(matching.getFirst().getUri().toString()).isEqualTo("lb://erudit-admin");
                    }
                }
            }
        }
    }

    @Test void swaggerPublishesEveryBundledServiceWithoutPrivateOperations() throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var config = send(request("/openapi/swagger-config").GET().build());
        assertThat(config.statusCode()).isEqualTo(200);
        var urls = mapper.readTree(config.body()).get("urls");
        assertThat(urls.size()).isEqualTo(8);
        for (var url : urls) {
            var response = send(request(url.get("url").asText()).GET().build());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).doesNotContain("/internal/", "Internal Users", "localhost:");
            assertThat(response.body()).contains("securitySchemes", "bearerAuth");
            var doc = mapper.readTree(response.body());
            assertThat(doc.get("servers").get(0).get("url").asText()).isEqualTo("/");
            assertThat(doc.get("paths").propertyNames()).allMatch(path -> path.startsWith("/api/v1/"));
            if (url.get("name").asText().equals("erudit-ai-platform")) {
                assertThat(doc.get("paths").size()).isZero();
                assertThat(doc.get("components").get("securitySchemes")).isNotNull();
            }
        }
        assertThat(send(request("/openapi/unknown").GET().build()).statusCode()).isEqualTo(404);
        var ui = send(request("/swagger-ui/index.html").GET().build());
        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("swagger-ui", "swagger-initializer.js");
        var initializer = send(request("/swagger-ui/swagger-initializer.js").GET().build());
        assertThat(initializer.body()).contains("/openapi/swagger-config");
    }

    @Test void refusesRetiredOrUndocumentedOperationsBeforeForwarding() throws Exception {
        int before=REQUESTS.get();
        assertError(send(request("/api/v1/auth/register").POST(HttpRequest.BodyPublishers.ofString("{}")).build()),404,"NOT_FOUND");
        assertError(send(request("/api/v1/program/me").GET().build()),404,"NOT_FOUND");
        assertError(send(request("/api/v1/users/123").POST(HttpRequest.BodyPublishers.ofString("{}")).build()),404,"NOT_FOUND");
        assertThat(REQUESTS.get()).isEqualTo(before);
    }

    @Test void rejectsMissingTokenAndInsufficientRoleButAllowsLogin() throws Exception {
        assertThat(send(plainRequest("/api/v1/content").GET().build()).statusCode()).isEqualTo(401);
        assertThat(send(plainRequest("/api/v1/auth/login").POST(HttpRequest.BodyPublishers.ofString("{}")).build()).statusCode()).isEqualTo(200);
        assertThat(send(plainRequest("/api/v1/users/register").header("Authorization", "Bearer " + token("LEARNER"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build()).statusCode()).isEqualTo(403);
        assertThat(send(plainRequest("/api/v1/content").header("Authorization", "Bearer " + token("LEARNER"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build()).statusCode()).isEqualTo(403);
    }

    private HttpRequest.Builder request(String path) { return plainRequest(path).header("Authorization", "Bearer " + token("SUPERVISOR")); }
    private HttpRequest.Builder plainRequest(String path) { return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); }
    private static String token(String role) {
        var key = new SecretKeySpec("local-erudit-jwt-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = NimbusJwtEncoder.withSecretKey(key).build();
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("erudit-user").subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("roles", List.of(role)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        assertThat(response.body()).contains("\"error\"", "\"code\":\"" + code + "\"", "\"message\"");
        assertThat(response.headers().firstValue("X-Request-ID")).isPresent();
    }
}
