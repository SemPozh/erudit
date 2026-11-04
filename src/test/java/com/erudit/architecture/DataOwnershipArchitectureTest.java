package com.erudit.architecture;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DataOwnershipArchitectureTest {
    @Test void composeProjectsCannotShareBusinessDatabaseDnsAliases() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.yml"));
        assertThat(compose).contains("name: ${COMPOSE_PROJECT_NAME:-erudit}-internal", "name: ${COMPOSE_PROJECT_NAME:-erudit}-edge")
                .doesNotContain("name: erudit-internal", "name: erudit-edge");
    }
    @Test void servicesOwnTheirImportsRepositoriesAndForeignKeys() throws Exception {
        var namespaces = Map.of("erudit-admin", "admin", "erudit-user", "user", "erudit-content", "content",
                "erudit-quiz", "quiz", "erudit-payment", "payment", "erudit-notificator", "notificator",
                "erudit-event-receiver", "eventreceiver", "erudit-ai-platform", "aiplatform");
        for (var service : namespaces.entrySet()) {
            Path module = Path.of("services", service.getKey());
            try (var files = Files.walk(module.resolve("src/main/java"))) {
                for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    for (String foreign : namespaces.values()) {
                        if (!foreign.equals(service.getValue())) {
                            assertThat(source).as(file.toString()).doesNotContain("com.erudit." + foreign + ".");
                        }
                    }
                }
            }
            assertThat(Files.readString(module.resolve("build.gradle.kts"))).doesNotContain(":services:");
            Path migrations = module.resolve("src/main/resources/db/migration");
            if (!Files.exists(migrations)) { assertThat(service.getKey()).isEqualTo("erudit-ai-platform"); continue; }
            StringBuilder sql = new StringBuilder();
            try (var files = Files.list(migrations)) {
                for (var file : files.toList()) { sql.append(Files.readString(file)).append('\n'); }
            }
            var tables = new HashSet<String>();
            var created = Pattern.compile("(?i)CREATE TABLE (?:IF NOT EXISTS )?([a-z_]+)").matcher(sql);
            while (created.find()) { tables.add(created.group(1).toLowerCase(Locale.ROOT)); }
            var references = Pattern.compile("(?i)REFERENCES\s+([a-z_.]+)").matcher(sql);
            while (references.find()) {
                assertThat(tables).as(service.getKey() + " foreign key " + references.group(1))
                        .contains(references.group(1).toLowerCase(Locale.ROOT));
            }
            String config = Files.readString(Path.of("config-repository", service.getKey() + ".properties"));
            assertThat(config).contains(service.getKey().equals("erudit-event-receiver") ? "spring.r2dbc.url=" : "spring.datasource.url=");
            assertThat(config).contains("${DB_USERNAME}", "${DB_PASSWORD}");
            for (String foreign : namespaces.keySet()) {
                if (!foreign.equals(service.getKey())) {
                    String db = foreign.replace("erudit-", "erudit_").replace("notificator", "notification").replace("event-receiver", "event");
                    assertThat(config).doesNotContain("/" + db + "}");
                }
            }
        }
    }
}
