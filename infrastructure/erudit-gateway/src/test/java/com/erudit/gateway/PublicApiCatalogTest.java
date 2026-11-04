package com.erudit.gateway;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PublicApiCatalogTest {
    private Map<String, Object> source(String path, String id) {
        return Map.of("openapi", "3.0.3", "info", Map.of("title", "Test", "version", "1"),
                "paths", Map.of(path, Map.of("get", Map.of("operationId", id))));
    }
    @Test void rejectsDuplicateExternalPathsAndInlineOperationIds() {
        assertThatThrownBy(() -> PublicApiCatalog.project(Map.of("one", source("/api/v1/test", "one"),
                "two", source("/api/v1/test", "two")))).hasMessageContaining("Duplicate public path");
        assertThatThrownBy(() -> PublicApiCatalog.project(Map.of("one", source("/api/v1/one", "duplicate"),
                "two", source("/api/v1/two", "duplicate")))).hasMessageContaining("operationId");
    }
    @Test void prunesUnusedInternalSchemasAndFollowsTransitiveReferences() {
        var source = Map.<String, Object>of("openapi", "3.0.3", "info", Map.of("title", "Test"),
                "paths", Map.of("/api/v1/test", Map.of("get", Map.of("operationId", "test", "responses",
                        Map.of("200", Map.of("$ref", "#/components/responses/Ok"))))),
                "components", Map.of("responses", Map.of("Ok", Map.of("$ref", "#/components/schemas/Public")),
                        "schemas", Map.of("Public", Map.of("type", "object"), "Internal", Map.of("type", "string"))));
        String projected = PublicApiCatalog.project(Map.of("test", source)).toString();
        assertThat(projected).contains("Public", "Ok").doesNotContain("Internal");
    }
    @Test void rejectsUnresolvedReferenceAndMissingOperationId() {
        assertThatThrownBy(() -> PublicApiCatalog.project(Map.of("test", Map.of("paths",
                Map.of("/api/v1/test", Map.of("get", Map.of())))))).hasMessageContaining("operationId");
        assertThatThrownBy(() -> PublicApiCatalog.project(Map.of("test", Map.of("paths",
                Map.of("/api/v1/test", Map.of("get", Map.of("operationId", "test", "$ref", "#/components/schemas/Missing")))))))
                .hasMessageContaining("Unresolved");
    }
}
