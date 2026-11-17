package com.erudit.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.config.enabled=false", "spring.config.name=isolated-test", "erudit.jwt.secret=local-erudit-jwt-secret-at-least-32-bytes"})
class ServiceApplicationTest {
    @Test
    void contextLoads() {
        // Each application must start independently, without the legacy module.
    }
}
