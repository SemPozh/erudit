package com.erudit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EruditApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
        assertThat(count).isEqualTo(1);
        jdbcTemplate.update("INSERT INTO app_metadata (setting_key, setting_value) VALUES (?, ?)", "test", "ready");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT setting_value FROM app_metadata WHERE setting_key = ?", String.class, "test"))
                .isEqualTo("ready");
    }
}
