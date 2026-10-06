package com.erudit.notification;

import com.erudit.web.ConflictException;
import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationTemplateService {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_.-]{0,49})\\}\\}");
    private final NotificationTemplateRepository repository;
    private final Clock clock;

    public NotificationTemplateService(NotificationTemplateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public NotificationTemplate create(String type, String channel, String title, String body) {
        NotificationTemplate value = value(UUID.randomUUID(), type, channel, title, body);
        if (repository.find(value.type(), value.channel()).isPresent()) {
            throw new ConflictException("Template for this type and channel already exists");
        }
        try {
            repository.insert(value, clock.instant());
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Template for this type and channel already exists");
        }
        return value;
    }

    @Transactional
    public NotificationTemplate update(UUID id, String type, String channel, String title, String body) {
        repository.find(id).orElseThrow(() -> new NotFoundException("Notification template not found"));
        NotificationTemplate value = value(id, type, channel, title, body);
        repository.find(value.type(), value.channel()).filter(existing -> !existing.id().equals(id))
                .ifPresent(existing -> { throw new ConflictException("Template for this type and channel already exists"); });
        try {
            repository.update(value, clock.instant());
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Template for this type and channel already exists");
        }
        return value;
    }

    @Transactional(readOnly = true)
    public NotificationTemplatePage list(Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? 20 : requestedSize;
        if (page < 0 || size < 1 || size > 50) {
            throw new ValidationException("page must be non-negative and size must be between 1 and 50");
        }
        return new NotificationTemplatePage(repository.list(page, size), repository.count(), page, size);
    }

    @Transactional(readOnly = true)
    public RenderedNotification render(NotificationType type, NotificationChannel channel,
                                       Map<String, String> parameters) {
        NotificationTemplate template = repository.find(type, channel)
                .orElseThrow(() -> new NotFoundException("Notification template not found"));
        Set<String> missing = new LinkedHashSet<>(template.parameters());
        missing.removeAll(parameters.keySet());
        if (!missing.isEmpty()) throw new ValidationException("Missing template parameters: " + missing);
        return new RenderedNotification(render(template.title(), parameters), render(template.body(), parameters));
    }

    private static NotificationTemplate value(UUID id, String type, String channel, String title, String body) {
        NotificationType parsedType = parse(NotificationType.class, type, "type");
        NotificationChannel parsedChannel = parse(NotificationChannel.class, channel, "channel");
        String normalizedTitle = title == null || title.isBlank() ? null : title.trim();
        String normalizedBody = body == null ? "" : body.trim();
        if (normalizedTitle != null && normalizedTitle.length() > 250) {
            throw new ValidationException("Template title cannot exceed 250 characters");
        }
        if (normalizedBody.isEmpty() || normalizedBody.length() > 10000) {
            throw new ValidationException("Template body must contain between 1 and 10000 characters");
        }
        Set<String> parameters = new LinkedHashSet<>();
        parameters.addAll(placeholders(normalizedTitle));
        parameters.addAll(placeholders(normalizedBody));
        return new NotificationTemplate(id, parsedType, parsedChannel, normalizedTitle, normalizedBody,
                Set.copyOf(parameters));
    }

    private static Set<String> placeholders(String text) {
        if (text == null) return Set.of();
        Matcher matcher = PLACEHOLDER.matcher(text);
        Set<String> result = new LinkedHashSet<>();
        String remainder = matcher.replaceAll(match -> {
            result.add(match.group(1));
            return "";
        });
        if (remainder.contains("{{") || remainder.contains("}}")) {
            throw new ValidationException("Malformed template placeholder");
        }
        return result;
    }

    private static String render(String text, Map<String, String> parameters) {
        if (text == null) return null;
        String result = text;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private static <T extends Enum<T>> T parse(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ValidationException("Unsupported notification template " + field);
        }
    }
}
