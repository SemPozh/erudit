package com.erudit.architecture;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.*;

class ComposePlatformTest {
    @Test void defaultStackIncludesEveryOwnerAndExposesOnlyGatewayWithBoundedResources() throws Exception {
        Map<String,Object> compose=new Yaml().load(Files.readString(Path.of("docker-compose.yml")));
        var services=map(compose.get("services"));int applications=0,databases=0;
        for(var entry:services.entrySet()){
            var service=map(entry.getValue());if(service.containsKey("profiles"))continue;
            assertThat(service).as(entry.getKey()).containsKeys("mem_limit","cpus","healthcheck");
            if(service.containsKey("build")){
                applications++;assertThat(map(service.get("environment")).get("JAVA_TOOL_OPTIONS")).as(entry.getKey()).asString().contains("MaxRAMPercentage=50.0");
            }else{databases++;}
            if(service.containsKey("ports")){assertThat(entry.getKey()).isEqualTo("gateway");}
        }
        assertThat(applications).isEqualTo(11);assertThat(databases).isEqualTo(8);
        Map<String,Object> demo=new Yaml().load(Files.readString(Path.of("docker-compose.demo.yml")));
        assertThat(map(demo.get("services"))).hasSize(8).allSatisfy((name,value)->{
            assertThat(map(value).get("profiles")).isEqualTo(List.of("demo"));
            assertThat(map(map(value).get("environment")).get("SPRING_PROFILES_ACTIVE")).isEqualTo("demo");
        });
        assertThat(Files.readString(Path.of("docker-compose.debug.yml"))).contains("127.0.0.1:");
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
}
