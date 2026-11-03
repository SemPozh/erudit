package com.erudit.discoveryserver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.name=isolated-test", "spring.cloud.config.enabled=false",
        "eureka.client.register-with-eureka=false", "eureka.client.fetch-registry=false",
        "eureka.server.use-read-only-response-cache=false", "eureka.server.wait-time-in-ms-when-sync-empty=0"
})
class RegistryTest {
    @Value("${local.server.port}")
    int port;

    @Test
    void registersAndRemovesInstanceThroughRegistryApi() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String body = """
                    {"instance":{"instanceId":"test-gateway","hostName":"localhost",
                    "app":"ERUDIT-GATEWAY","ipAddr":"127.0.0.1","status":"UP",
                    "port":{"$":8080,"@enabled":"true"},
                    "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"},
                    "healthCheckUrl":"http://localhost:8080/actuator/health"}}
                    """;
            var register = HttpRequest.newBuilder(uri("/eureka/apps/ERUDIT-GATEWAY"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            assertThat(client.send(register, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
            var lookup = HttpRequest.newBuilder(uri("/eureka/apps/ERUDIT-GATEWAY/test-gateway"))
                    .header("Accept", "application/json").GET().build();
            var found = client.send(lookup, HttpResponse.BodyHandlers.ofString());
            assertThat(found.statusCode()).isEqualTo(200);
            assertThat(found.body()).contains("test-gateway", "/actuator/health");
            var remove = HttpRequest.newBuilder(lookup.uri()).DELETE().build();
            assertThat(client.send(remove, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            assertThat(client.send(lookup, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
            var self = HttpRequest.newBuilder(uri("/eureka/apps/ERUDIT-DISCOVERY-SERVER")).GET().build();
            assertThat(client.send(self, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
