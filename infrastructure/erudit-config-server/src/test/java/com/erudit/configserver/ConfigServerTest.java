package com.erudit.configserver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.config.enabled=false")
class ConfigServerTest {
    @Value("${local.server.port}")
    int port;

    @DynamicPropertySource
    static void repository(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.config.server.native.search-locations",
                () -> Path.of(System.getProperty("config.repository")).toUri().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "user", "content", "quiz", "event-receiver",
            "ai-platform", "payment", "notificator", "gateway", "discovery-server", "config-server"})
    void servesEveryApplicationAndProfile(String service) throws Exception {
        for (String profile : new String[]{"default", "demo"}) {
            var response = get("/erudit-" + service + "/" + profile);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("erudit-" + service + ".properties",
                    "erudit.pagination.max-size", "50", "connectTimeout");
            if (profile.equals("demo")) {
                assertThat(response.body()).contains("erudit.demo.enabled");
            }
        }
    }

    @Test
    void healthAndProbesAreAvailable() throws Exception {
        for (String endpoint : new String[]{"health", "health/liveness", "health/readiness"}) {
            var response = get("/actuator/" + endpoint);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("UP");
        }
    }

    @Test
    void credentialsRemainUnresolvedEnvironmentPlaceholders() throws Exception {
        int checked = 0;
        try (var paths = Files.list(Path.of(System.getProperty("config.repository")))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".properties")).toList()) {
                var properties = new Properties();
                try (var reader = Files.newBufferedReader(path)) {
                    properties.load(reader);
                }
                for (String key : properties.stringPropertyNames()) {
                    if (key.matches("(?i).*(password|secret|token|username|api-key).*")) {
                        assertThat(properties.getProperty(key)).as(path + ": " + key)
                                .matches("\\$\\{[A-Z][A-Z0-9_]*}");
                        checked++;
                    }
                }
            }
        }
        assertThat(checked).isGreaterThanOrEqualTo(16);
        assertThat(get("/erudit-user/default").body()).contains("${DB_PASSWORD}", "${DB_USERNAME}");
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
