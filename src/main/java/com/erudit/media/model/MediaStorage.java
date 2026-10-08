package com.erudit.media.model;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface MediaStorage {
    String store(UUID id, MultipartFile file) throws IOException;
    Resource load(String storageKey);
    void delete(String storageKey);
}
