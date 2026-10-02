package com.erudit.media;

import com.erudit.web.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Component
public class LocalMediaStorage implements MediaStorage {
    private final Path root;

    public LocalMediaStorage(@Value("${media.storage-path:./data/media}") String storagePath) throws IOException {
        root = Path.of(storagePath).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(UUID id, MultipartFile file) throws IOException {
        String extension = extension(file.getOriginalFilename());
        String key = id + extension;
        Path target = resolve(key);
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return key;
    }

    @Override
    public Resource load(String storageKey) {
        FileSystemResource resource = new FileSystemResource(resolve(storageKey));
        if (!resource.exists() || !resource.isReadable()) {
            throw new NotFoundException("Media file not found");
        }
        return resource;
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ignored) {
            // A failed cleanup must not hide the original persistence error.
        }
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) return "";
        String value = filename.substring(index).toLowerCase(Locale.ROOT);
        return value.matches("\\.[a-z0-9]{1,10}") ? value : "";
    }
}
