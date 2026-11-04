package com.erudit.aiplatform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockGenerationProvider implements GenerationProvider {
    @Override
    public QuizResult quiz(String seed, String sourceText) {
        String marker = marker(seed, sourceText);
        String subject = compact(sourceText, 48);
        return new QuizResult("What is the key idea of " + subject + "? [" + marker + "]",
                List.of("The stated key idea", "An unrelated statement", "Insufficient context"), 0);
    }

    @Override
    public CardResult card(String seed, String sourceText) {
        return new CardResult("Card " + marker(seed, sourceText), compact(sourceText, 240));
    }

    private String marker(String seed, String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((seed + "\n" + text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String compact(String value, int length) {
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(length, normalized.length()));
    }
}
