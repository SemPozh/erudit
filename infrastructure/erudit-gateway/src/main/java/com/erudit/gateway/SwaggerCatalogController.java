package com.erudit.gateway;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@io.swagger.v3.oas.annotations.Hidden
@RestController
public class SwaggerCatalogController {
    private final PublicApiCatalog catalog;
    public SwaggerCatalogController(PublicApiCatalog catalog) { this.catalog = catalog; }

    @GetMapping("/openapi/swagger-config")
    public Map<String, Object> configuration() {
        return Map.of("urls", catalog.documents().keySet().stream()
                .map(name -> Map.of("name", name, "url", "/openapi/" + name)).toList(),
                "validatorUrl", "", "persistAuthorization", false);
    }

    @GetMapping("/openapi/{service}")
    public Map<String, Object> document(@PathVariable String service) {
        var document = catalog.documents().get(service);
        if (document == null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        return document;
    }
}
