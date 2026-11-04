package com.erudit.architecture;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class DatabaseIsolationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withUsername("bootstrap").withPassword("bootstrap-test-password").withDatabaseName("role_test")
            .withEnv("APP_DB_USER", "minimal_app").withEnv("APP_DB_PASSWORD", "quoted'password")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/postgres/init-domain.sh").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/10-domain.sh");

    @Test void everyDomainMigratesAsAnUnprivilegedOwnerAndCannotConnectToAnotherDatabase() throws Exception {
        var modules = Map.of("admin", "admin", "content", "content", "event", "event-receiver",
                "notification", "notificator", "payment", "payment", "quiz", "quiz", "user", "user");
        try (var owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var ddl = owner.createStatement()) {
            for (var entry : modules.entrySet()) {
                String domain = entry.getKey(), database = "erudit_" + domain;
                ddl.execute("CREATE DATABASE " + database);
                var init = POSTGRES.execInContainer("env", "POSTGRES_DB=" + database, "APP_DB_USER=" + database,
                        "APP_DB_PASSWORD=domain-test-password", "sh", "/docker-entrypoint-initdb.d/10-domain.sh");
                assertThat(init.getExitCode()).as(domain + " init: " + init.getStderr()).isZero();
                String url = url(database);
                try (var connection = DriverManager.getConnection(url, database, "domain-test-password");
                     var sql = connection.createStatement()) {
                    try (var roles = sql.executeQuery("SELECT rolsuper,rolcreatedb,rolcreaterole,rolreplication FROM pg_roles WHERE rolname=current_user")) {
                        assertThat(roles.next()).isTrue();
                        for (int column = 1; column <= 4; column++) { assertThat(roles.getBoolean(column)).isFalse(); }
                    }
                    assertThatThrownBy(() -> sql.execute("CREATE DATABASE forbidden")).isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> sql.execute("CREATE ROLE forbidden")).isInstanceOf(SQLException.class);
                    if (domain.equals("event")) {
                        // Upgrade the previous schema.sql deployment without dropping receipts.
                        sql.execute(Files.readString(migrations(entry.getValue()).resolve("V1__event_receipts.sql")));
                        sql.execute("INSERT INTO event_receipts(event_id,event_type,occurred_at,payload,status) VALUES ('00000000-0000-0000-0000-000000000092','TEST',now(),'{}','PENDING')");
                    }
                    var flyway = Flyway.configure().dataSource(url, database, "domain-test-password")
                            .locations("filesystem:" + migrations(entry.getValue()).toAbsolutePath(),
                                    "filesystem:" + migrations(entry.getValue()).resolveSibling("demo").toAbsolutePath())
                            .baselineOnMigrate(domain.equals("event")).baselineVersion("0").load();
                    assertThat(flyway.migrate().migrationsExecuted).isPositive();
                    assertThat(flyway.migrate().migrationsExecuted).isZero();
                    try (var history = sql.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
                        history.next(); assertThat(history.getInt(1)).isPositive();
                    }
                    if (domain.equals("event")) {
                        try (var receipt = sql.executeQuery("SELECT count(*) FROM event_receipts")) {
                            receipt.next(); assertThat(receipt.getInt(1)).isEqualTo(1);
                        }
                    }
                }
            }
            for (String domain : modules.keySet()) {
                String user = "erudit_" + domain;
                for (String foreign : modules.keySet()) {
                    if (!foreign.equals(domain)) {
                        assertThatThrownBy(() -> DriverManager.getConnection(url("erudit_" + foreign), user, "domain-test-password"))
                                .as(domain + " must not connect to " + foreign).isInstanceOf(SQLException.class)
                                .hasMessageContaining("permission denied for database");
                    }
                }
                assertThatThrownBy(() -> DriverManager.getConnection(url("postgres"), user, "domain-test-password"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("permission denied for database");
            }
        }
    }
    private static Path migrations(String module) {
        return Path.of("services/erudit-" + module + "/src/main/resources/db/migration");
    }
    private static String url(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }
}
