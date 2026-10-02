package com.erudit.media;

import com.erudit.openapi.api.MediaApi;
import com.erudit.openapi.model.MediaFile;
import com.erudit.openapi.model.MediaFileResponse;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@RestController
public class MediaController implements MediaApi {
    private final MediaService service;
    private final HttpServletRequest request;

    public MediaController(MediaService service, HttpServletRequest request) {
        this.service = service;
        this.request = request;
    }

    @Override
    public ResponseEntity<MediaFileResponse> uploadMedia(MultipartFile file) {
        requireAuthenticated();
        MediaAsset media = service.upload(file);
        MediaFile data = new MediaFile(media.id(), "/api/v1/media/" + media.id(),
                media.contentType(), media.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(new MediaFileResponse(data));
    }

    @Override
    public ResponseEntity<Resource> getMedia(UUID id) {
        requireAuthenticated();
        LoadedMedia media = service.load(id);
        MediaAsset metadata = media.metadata();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.size())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(metadata.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(media.resource());
    }

    private void requireAuthenticated() {
        if (request.getUserPrincipal() == null) {
            throw new UnauthorizedException("Authentication is required");
        }
    }
}
