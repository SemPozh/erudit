package com.erudit.content.controller;

import com.erudit.content.openapi.api.MediaApi;
import com.erudit.content.openapi.model.MediaResponse;
import com.erudit.content.service.MediaService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class MediaController implements MediaApi {
    private final MediaService service;
    @Override public ResponseEntity<MediaResponse> uploadMedia(MultipartFile file) {
        var asset = service.upload(file);
        return ResponseEntity.status(201).body(new MediaResponse(asset.id(), "/api/v1/media/" + asset.id(), asset.contentType(), asset.size()));
    }
    @Override public ResponseEntity<Resource> getMedia(UUID id) {
        var loaded = service.load(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(loaded.asset().contentType()))
                .contentLength(loaded.asset().size()).header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(loaded.asset().originalName(), StandardCharsets.UTF_8).build().toString())
                .body(loaded.resource());
    }
}
