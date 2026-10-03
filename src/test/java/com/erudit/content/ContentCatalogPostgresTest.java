package com.erudit.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "clickhouse.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class ContentCatalogPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private ContentRepository contentRepository;
    @Autowired private ContentCatalogService catalogService;

    @Test
    void flywaySchemaAndPagedCategoryQueryWorkOnPostgres() {
        UUID id = UUID.randomUUID();
        contentRepository.saveCategory(new Category(id, "Postgres category"));

        CategoryPage page = catalogService.categories(0, 50);

        assertThat(page.items()).extracting(Category::id).contains(id);
        assertThat(page.total()).isPositive();
    }
}
