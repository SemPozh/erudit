package com.erudit.configserver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigClientTest {
    @TempDir
    Path repository;

    @Test
    void restartLoadsChangedRemoteConfiguration() throws Exception {
        Path config = repository.resolve("erudit-ai-platform.properties");
        Files.writeString(config, "erudit.pagination.max-size=40\n");
        var application = new SpringApplication(ServiceApplication.class);
        try (var server = application.run("--server.port=0", "--spring.cloud.config.enabled=false",
                "--spring.cloud.config.server.native.search-locations=" + repository.toUri())) {
            int port = ((WebServerApplicationContext) server).getWebServer().getPort();
            try (var client = startClient(port)) {
                assertThat(client.getEnvironment().getProperty("erudit.pagination.max-size")).isEqualTo("40");
                Files.writeString(config, "erudit.pagination.max-size=30\n");
                assertThat(client.getEnvironment().getProperty("erudit.pagination.max-size")).isEqualTo("40");
            }
            try (var client = startClient(port)) {
                assertThat(client.getEnvironment().getProperty("erudit.pagination.max-size")).isEqualTo("30");
            }
        }
    }

    @Test
    void retriesAndFailsStartupWhenServerIsUnavailable() throws Exception {
        var requests = new AtomicInteger();
        var unavailable = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        unavailable.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        unavailable.start();
        try {
            assertThatThrownBy(() -> startClient(unavailable.getAddress().getPort()))
                    .hasStackTraceContaining("Could not locate PropertySource");
            assertThat(requests.get()).isEqualTo(3);
        } finally {
            unavailable.stop(0);
        }
    }

    private ConfigurableApplicationContext startClient(int port) {
        var client = new SpringApplication(ClientApplication.class);
        client.setWebApplicationType(WebApplicationType.NONE);
        return client.run("--spring.config.name=config-client-test", "--spring.application.name=erudit-ai-platform",
                "--spring.cloud.config.enabled=true",
                "--spring.config.import=configserver:http://127.0.0.1:" + port,
                "--spring.cloud.config.fail-fast=true", "--spring.cloud.config.retry.max-attempts=3",
                "--spring.cloud.config.retry.initial-interval=10",
                "--spring.cloud.config.request-connect-timeout=1000",
                "--spring.cloud.config.request-read-timeout=1000");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ClientApplication { }
}
