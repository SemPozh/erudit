package com.erudit.user;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.erudit.user.controller.InternalUserController;
import com.erudit.user.model.SocialModels.InternalUser;
import com.erudit.user.service.InternalUserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(controllers=InternalUserController.class, properties={"spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.config.name=isolated-test"})
class InternalUserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean InternalUserService repository;
    @Test void internalLookupReturnsOnlyOwnedUserDataAnd404ForMissingUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.lookup(id)).thenReturn(new InternalUser(id,"Demo",true,List.of()));
        mvc.perform(get("/internal/v1/users/{id}",id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.active").value(true));
        when(repository.lookup(id)).thenThrow(new IllegalArgumentException("User not found"));
        mvc.perform(get("/internal/v1/users/{id}",id)).andExpect(status().isNotFound());
    }
}
