package com.erudit.architecture;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.*;

class ApprovedApiTest {
    @Test void serviceContractsMatchApprovedPublicOperationsAndLegacyCannotReturn() throws Exception {
        var yaml=new Yaml();
        Set<String> approved=new TreeSet<>(),actual=new TreeSet<>();
        List<Map<String,Object>> baseline=yaml.load(Files.readString(Path.of("docs/api/public-operations.json")));
        baseline.forEach(row->approved.add(row.get("owner")+" "+row.get("method")+" "+row.get("path")+" "+row.get("operationId")));
        try(var specs=Files.walk(Path.of("services"))){
            for(var file:specs.filter(path->path.endsWith("src/main/resources/static/openapi.yaml")).toList()){
                Map<String,Object> doc=yaml.load(Files.readString(file));
                for(var path:map(doc.get("paths")).entrySet()){
                    if(!path.getKey().startsWith("/api/v1/"))continue;
                    map(path.getValue()).forEach((method,value)->{
                        if(Set.of("get","post","put","patch","delete","head","options").contains(method))
                            actual.add(file.getName(1)+" "+method.toUpperCase(Locale.ROOT)+" "+path.getKey()+" "+map(value).get("operationId"));
                    });
                }
            }
        }
        assertThat(actual).containsExactlyElementsOf(approved);
        assertThat(Path.of("src/main")).doesNotExist();assertThat(Path.of("Dockerfile")).doesNotExist();
        assertThat(Files.readString(Path.of("docker-compose.yml"))).doesNotContain("profiles: [legacy]","jdbc:postgresql://postgres:","image: erudit:latest");
        try(var configs=Files.list(Path.of("config-repository"))){
            for(var config:configs.toList()){
                String source=Files.readString(config);
                assertThat(source).doesNotContain("spring.cloud.openfeign.client.config.default.url=","spring.security.");
            }
        }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
}
