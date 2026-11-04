package com.erudit.content.service;

import com.erudit.content.model.MediaAsset;
import com.erudit.content.repository.MediaRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private static final Map<String, Predicate<byte[]>> SIGNATURES = Map.of(
            "image/jpeg", bytes -> starts(bytes, 0xff, 0xd8, 0xff),
            "image/png", bytes -> starts(bytes, 0x89, 0x50, 0x4e, 0x47),
            "audio/mpeg", bytes -> bytes.length > 2 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3',
            "video/mp4", bytes -> bytes.length > 7 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p');
    private final MediaRepository repository;
    private final Path root;
    private final long maxSize;

    public MediaService(MediaRepository repository, @Value("${media.storage-path:./data/media}") String path,
                        @Value("${media.max-size-bytes:52428800}") long maxSize) throws IOException {
        this.repository = repository;
        this.root = Path.of(path).toAbsolutePath().normalize();
        this.maxSize = maxSize;
        Files.createDirectories(root);
    }

    @Transactional
    public MediaAsset upload(MultipartFile file) {
        validate(file);
        UUID id = UUID.randomUUID();
        String key = id.toString();
        Path target = resolve(key);
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            String name = safeName(file.getOriginalFilename());
            MediaAsset asset = new MediaAsset(id, key, name, file.getContentType(), file.getSize(), Instant.now());
            repository.save(asset);
            return asset;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store media", exception);
        } catch (RuntimeException exception) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Loaded load(UUID id) {
        MediaAsset asset = repository.find(id).orElseThrow(() -> new CategoryNotFoundException(id));
        FileSystemResource resource = new FileSystemResource(resolve(asset.storageKey()));
        if (!resource.exists()) throw new CategoryNotFoundException(id);
        return new Loaded(asset, resource);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File must not be empty");
        if (file.getSize() > maxSize) throw new IllegalArgumentException("File exceeds maximum size");
        Predicate<byte[]> signature = SIGNATURES.get(file.getContentType());
        if (signature == null) throw new IllegalArgumentException("Unsupported media type");
        try {
            if (!signature.test(file.getInputStream().readNBytes(16))) throw new IllegalArgumentException("File signature does not match content type");
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    private Path resolve(String key) {
        Path value = root.resolve(key).normalize();
        if (!value.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return value;
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "file";
        String value = name.replace('\\', '/');
        return value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\r\\n\"]", "_");
    }

    private static boolean starts(byte[] value, int... expected) {
        if (value.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((value[i] & 0xff) != expected[i]) return false;
        return true;
    }
    public record Loaded(MediaAsset asset, Resource resource) {}
}
