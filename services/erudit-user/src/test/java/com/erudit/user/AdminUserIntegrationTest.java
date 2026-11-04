package com.erudit.user;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
@Testcontainers @AutoConfigureMockMvc
@SpringBootTest(properties={"spring.cloud.config.enabled=false","eureka.client.enabled=false","spring.config.name=isolated-test"})
class AdminUserIntegrationTest{
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired com.erudit.user.repository.SocialRepository social;
    @Test void internalSearchBlockAndLookupUseOnlyUserOwnedData()throws Exception{
        UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");
        mvc.perform(get("/internal/v1/admin/users").param("query","demo@erudit.local")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count","1"));
        UUID device=social.saveDevice(id,"admin-merge-device-token","ANDROID").id();
        mvc.perform(get("/internal/v1/users/{id}",id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.deviceIds[0]").value(device.toString()));
        for(int i=0;i<2;i++)mvc.perform(post("/internal/v1/admin/users/{id}/block",id)).andExpect(status().isNoContent());
        mvc.perform(get("/internal/v1/users/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.deviceIds[0]").value(device.toString()));
        mvc.perform(get("/internal/v1/admin/users")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("BLOCKED"));
        mvc.perform(post("/internal/v1/admin/users/{id}/block",UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/internal/v1/admin/users").param("size","51")).andExpect(status().isBadRequest());
    }
}
