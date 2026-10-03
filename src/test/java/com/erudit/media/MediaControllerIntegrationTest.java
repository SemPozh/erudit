package com.erudit.media;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaControllerIntegrationTest {
    private static final Path STORAGE = createStorage();
    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4
    };

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("media.storage-path", () -> STORAGE.toString());
        registry.add("media.max-size-bytes", () -> 64);
    }

    @Test
    void uploadsPersistsAndReturnsMedia() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "picture.png", "image/png", PNG);
        String response = mvc.perform(multipart("/api/v1/media").file(file).with(user("user-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.size").value(PNG.length))
                .andReturn().getResponse().getContentAsString();
        var matcher = Pattern.compile("\\\"id\\\":\\\"([0-9a-f-]{36})\\\"").matcher(response);
        assertThat(matcher.find()).isTrue();
        String id = matcher.group(1);

        mvc.perform(get("/api/v1/media/{id}", id).with(user("user-1")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Length", String.valueOf(PNG.length)))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PNG));
        try (var files = Files.list(STORAGE)) {
            assertThat(files.filter(Files::isRegularFile).count()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void rejectsUnsupportedSpoofedAndOversizedFiles() throws Exception {
        mvc.perform(multipart("/api/v1/media").file(
                                new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[] {1}))
                        .with(user("user-1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(multipart("/api/v1/media").file(
                                new MockMultipartFile("file", "fake.png", "image/png", "not png".getBytes()))
                        .with(user("user-1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(multipart("/api/v1/media").file(
                                new MockMultipartFile("file", "large.png", "image/png", new byte[65]))
                        .with(user("user-1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void requiresAuthenticatedPrincipal() throws Exception {
        mvc.perform(multipart("/api/v1/media").file(
                        new MockMultipartFile("file", "picture.png", MediaType.IMAGE_PNG_VALUE, PNG)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private static Path createStorage() {
        try {
            return Files.createTempDirectory("erudit-media-test-");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}