package com.erudit.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailureController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void mapsBusinessExceptions() throws Exception {
        assertError("not-found", 404, "NOT_FOUND");
        assertError("conflict", 409, "CONFLICT");
        assertError("validation", 400, "BAD_REQUEST");
        assertError("forbidden", 403, "FORBIDDEN");
        assertError("unauthorized", 401, "UNAUTHORIZED");
    }

    private void assertError(String kind, int statusCode, String code) throws Exception {
        mvc.perform(get("/fail/{kind}", kind))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value("Example error"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mvc.perform(get("/fail/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Internal server error"));
    }

    @Test
    void validatesRequestBody() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("name: must not be blank"));
    }

    @Test
    void handlesMalformedJson() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/fail/{kind}")
        ApiResponse<Void> fail(@PathVariable String kind) {
            throw switch (kind) {
                case "not-found" -> new NotFoundException("Example error");
                case "conflict" -> new ConflictException("Example error");
                case "validation" -> new ValidationException("Example error");
                case "forbidden" -> new ForbiddenException("Example error");
                case "unauthorized" -> new UnauthorizedException("Example error");
                default -> new IllegalStateException("Secret details");
            };
        }

        @PostMapping("/validate")
        ApiResponse<String> validate(@Valid @RequestBody Input input) {
            return ApiResponse.success(input.name());
        }
    }

    record Input(@NotBlank String name) {
    }
}
