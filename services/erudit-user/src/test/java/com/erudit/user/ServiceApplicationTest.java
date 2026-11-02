package com.erudit.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.config.name=isolated-test"})
class ServiceApplicationTest {
    @Test
    void contextLoads() {
        // Each application must start independently, without the legacy module.
    }
}
