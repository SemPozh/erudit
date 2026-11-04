package com.erudit.architecture;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.*;

/** The published acceptance inventory must include internal as well as public operations. */
class ApiInventoryTest {
    @Test void inventoryMatchesEveryServiceOperation() throws Exception {
        var expected=new TreeSet<String>();
        var yaml=new Yaml();
        try(var files=Files.walk(Path.of("services"))){
            for(var file:files.filter(path->path.endsWith("src/main/resources/static/openapi.yaml")).toList()){
                Map<String,Object> doc=yaml.load(Files.readString(file));
                for(var path:map(doc.get("paths")).entrySet()){
                    map(path.getValue()).forEach((method,operation)->{
                        if(Set.of("get","post","put","patch","delete","head","options","trace").contains(method))
                            expected.add("| "+file.getName(1)+" | "+method.toUpperCase(Locale.ROOT)+" | `"+path.getKey()+"` | `"+map(operation).get("operationId")+"` |");
                    });
                }
            }
        }
        var actual=Files.readAllLines(Path.of("docs/api/endpoints.md")).stream()
                .filter(line->line.startsWith("| erudit-")).toList();
        assertThat(actual).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
}
