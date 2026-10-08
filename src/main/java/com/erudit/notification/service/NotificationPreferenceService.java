package com.erudit.notification.service;

import com.erudit.notification.model.NotificationChannel;
import com.erudit.notification.model.NotificationPreference;
import com.erudit.notification.model.NotificationType;
import com.erudit.notification.repository.NotificationPreferenceRepository;

import com.erudit.web.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationPreferenceService {
    private final NotificationPreferenceRepository repository;
    private final Clock clock;

    public NotificationPreferenceService(NotificationPreferenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPreference get(UUID userId) {
        return repository.find(userId).orElseGet(() -> defaults(userId));
    }

    @Transactional
    public NotificationPreference update(UUID userId, Collection<NotificationChannel> requestedChannels,
                                         Collection<NotificationType> requestedTypes,
                                         String requestedStart,
                                         String requestedEnd) {
        Set<NotificationChannel> channels = copy(requestedChannels, "channels");
        Set<NotificationType> types = copy(requestedTypes, "types");
        if (channels.isEmpty() || types.isEmpty()) {
            throw new ValidationException("At least one notification channel and type are required");
        }
        if ((requestedStart == null) != (requestedEnd == null)) {
            throw new ValidationException("Both quiet hours boundaries must be provided");
        }
        LocalTime start = parseTime(requestedStart);
        LocalTime end = parseTime(requestedEnd);
        NotificationPreference value = new NotificationPreference(userId, channels, types, start, end);
        repository.save(value, clock.instant());
        return value;
    }

    @Transactional(readOnly = true)
    public boolean allows(UUID userId, NotificationChannel channel, NotificationType type,
                          Instant instant, ZoneId zoneId) {
        return get(userId).allows(channel, type, instant.atZone(zoneId).toLocalTime());
    }

    private static NotificationPreference defaults(UUID userId) {
        return new NotificationPreference(userId, Set.of(NotificationChannel.values()),
                Set.of(NotificationType.values()), null, null);
    }

    private static <T> Set<T> copy(Collection<T> values, String field) {
        if (values == null) throw new ValidationException(field + " are required");
        Set<T> result = new LinkedHashSet<>(values);
        if (result.contains(null)) throw new ValidationException(field + " cannot contain null");
        if (result.size() != values.size()) throw new ValidationException(field + " must be unique");
        return Set.copyOf(result);
    }

    private static LocalTime parseTime(String value) {
        if (value == null) return null;
        try {
            return LocalTime.parse(value);
        } catch (DateTimeException exception) {
            throw new ValidationException("Quiet hours must use HH:mm format");
        }
    }
}
