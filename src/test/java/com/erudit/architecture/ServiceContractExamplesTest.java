package com.erudit.architecture;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.*;

class ServiceContractExamplesTest {
    @Test void publicContractsHaveUniqueOperationsAndValidFixtureExamples() throws Exception {
        var options=new LoaderOptions();options.setAllowDuplicateKeys(false);
        var yaml=new Yaml(new SafeConstructor(options));
        var paths=new HashSet<String>();var ids=new HashSet<String>();int examples=0;
        StringBuilder fixtures=new StringBuilder();
        try(var files=Files.walk(Path.of("services"))){
            for(var file:files.filter(p -> p.toString().endsWith(".sql")&&!p.toString().contains("build")).toList()){
                fixtures.append(Files.readString(file));
            }
        }
        try(var files=Files.walk(Path.of("services"))){
            for(var file:files.filter(p -> p.endsWith("src/main/resources/static/openapi.yaml")).toList()){
                Map<String,Object> doc=yaml.load(Files.readString(file));
                for(var entry:map(doc.get("paths")).entrySet()){
                    if(!entry.getKey().startsWith("/api/v1/"))continue;
                    assertThat(paths.add(entry.getKey())).as("Duplicate path "+entry.getKey()).isTrue();
                    for(var operation:map(entry.getValue()).entrySet()){
                        if(!Set.of("get","put","post","patch","delete","head","options").contains(operation.getKey()))continue;
                        String id=(String)map(operation.getValue()).get("operationId");
                        assertThat(id).isNotBlank();assertThat(ids.add(id)).as("Duplicate operation "+id).isTrue();
                    }
                    examples+=examples(doc,entry.getValue(),fixtures.toString(),true);
                }
                examples+=examples(doc,doc.get("components"),fixtures.toString(),false);
            }
        }
        assertThat(paths.size()).isGreaterThan(40);assertThat(examples).isGreaterThan(20);
    }
    private int examples(Map<String,Object> doc,Object node,String fixtures,boolean path) {
        int count=0;
        if(node instanceof Map<?,?> raw){
            var schema=map(raw);
            if(schema.containsKey("example")){
                validate(doc,schema,schema.get("example"));count++;
                if(path&&"uuid".equals(schema.get("format"))){assertThat(fixtures).contains(schema.get("example").toString());}
            }
            for(Object value:raw.values())count+=examples(doc,value,fixtures,path);
        }else if(node instanceof List<?> list){for(Object value:list)count+=examples(doc,value,fixtures,path);}
        return count;
    }
    private void validate(Map<String,Object> doc,Map<String,Object> schema,Object value){
        if(schema.containsKey("$ref")){
            Object target=doc;for(String part:schema.get("$ref").toString().substring(2).split("/"))target=map(target).get(part);
            assertThat(target).isNotNull();validate(doc,map(target),value);return;
        }
        if(value==null){assertThat(schema.get("nullable")).isEqualTo(true);return;}
        if(schema.get("allOf") instanceof List<?> parents)parents.forEach(parent->validate(doc,map(parent),value));
        if(schema.get("enum") instanceof List<?> allowed)assertThat(allowed.contains(value)).as("Enum example "+value).isTrue();
        switch(String.valueOf(schema.get("type"))){
            case "object" -> {
                var body=map(value);assertThat(value).isInstanceOf(Map.class);
                if(schema.get("required") instanceof List<?> required)required.forEach(key->assertThat(body).containsKey(key.toString()));
                body.forEach((key,item)->{var property=map(schema.get("properties")).get(key);if(property!=null)validate(doc,map(property),item);});
            }
            case "array" -> {
                assertThat(value).isInstanceOf(List.class);var items=(List<?>)value;
                if(schema.get("minItems") instanceof Number min)assertThat(items.size()).isGreaterThanOrEqualTo(min.intValue());
                items.forEach(item->validate(doc,map(schema.get("items")),item));
            }
            case "string" -> {
                assertThat(value).isInstanceOf(String.class);
                if("uuid".equals(schema.get("format")))assertThatCode(()->UUID.fromString(value.toString())).doesNotThrowAnyException();
                if(schema.get("minLength") instanceof Number min)assertThat(value.toString().length()).isGreaterThanOrEqualTo(min.intValue());
            }
            case "integer","number" -> assertThat(value).isInstanceOf(Number.class);
            case "boolean" -> assertThat(value).isInstanceOf(Boolean.class);
            default -> { }
        }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return value instanceof Map<?,?>?(Map<String,Object>)value:Map.of();}
}
