package com.erudit.media;

import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class MediaService {
    private static final Map<String, Predicate<byte[]>> SIGNATURES = Map.ofEntries(
            Map.entry("image/jpeg", bytes -> starts(bytes, 0xff, 0xd8, 0xff)),
            Map.entry("image/png", bytes -> starts(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)),
            Map.entry("image/gif", bytes -> ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a")),
            Map.entry("image/webp", bytes -> ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")),
            Map.entry("audio/mpeg", bytes -> ascii(bytes, 0, "ID3") || (bytes.length > 1 && u(bytes[0]) == 0xff && (u(bytes[1]) & 0xe0) == 0xe0)),
            Map.entry("audio/wav", bytes -> ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WAVE")),
            Map.entry("audio/ogg", bytes -> ascii(bytes, 0, "OggS")),
            Map.entry("video/ogg", bytes -> ascii(bytes, 0, "OggS")),
            Map.entry("video/mp4", bytes -> ascii(bytes, 4, "ftyp")),
            Map.entry("video/webm", bytes -> starts(bytes, 0x1a, 0x45, 0xdf, 0xa3))
    );

    private final MediaRepository repository;
    private final MediaStorage storage;
    private final long maxSize;

    public MediaService(MediaRepository repository, MediaStorage storage,
                        @Value("${media.max-size-bytes:52428800}") long maxSize) {
        this.repository = repository;
        this.storage = storage;
        this.maxSize = maxSize;
    }

    @Transactional
    public MediaAsset upload(MultipartFile file) {
        validate(file);
        UUID id = UUID.randomUUID();
        String key = null;
        try {
            key = storage.store(id, file);
            MediaAsset media = new MediaAsset(id, key, safeFilename(file.getOriginalFilename()),
                    file.getContentType(), file.getSize(), Instant.now());
            repository.save(media);
            return repository.findById(id).orElseThrow();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store media", exception);
        } catch (RuntimeException exception) {
            if (key != null) storage.delete(key);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public LoadedMedia load(UUID id) {
        MediaAsset metadata = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Media not found"));
        return new LoadedMedia(metadata, storage.load(metadata.storageKey()));
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) throw new ValidationException("File must not be empty");
        if (file.getSize() > maxSize) throw new ValidationException("File exceeds maximum size");
        Predicate<byte[]> signature = SIGNATURES.get(file.getContentType());
        if (signature == null) throw new ValidationException("Unsupported media type");
        try {
            byte[] prefix = file.getInputStream().readNBytes(16);
            if (!signature.test(prefix)) throw new ValidationException("File content does not match media type");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read media", exception);
        }
    }

    private static String safeFilename(String value) {
        if (value == null || value.isBlank()) return "file";
        String normalized = value.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\\"]", "_");
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static boolean starts(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (u(bytes[i]) != expected[i]) return false;
        return true;
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) return false;
        for (int i = 0; i < expected.length(); i++) if (bytes[offset + i] != expected.charAt(i)) return false;
        return true;
    }

    private static int u(byte value) { return value & 0xff; }
}
