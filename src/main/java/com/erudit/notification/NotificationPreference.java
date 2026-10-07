package com.erudit.notification;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public record NotificationPreference(UUID userId, Set<NotificationChannel> channels,
                                     Set<NotificationType> types, LocalTime quietHoursStart,
                                     LocalTime quietHoursEnd) {
    public boolean allows(NotificationChannel channel, NotificationType type, LocalTime localTime) {
        return channels.contains(channel) && types.contains(type) && !isQuiet(localTime);
    }

    private boolean isQuiet(LocalTime time) {
        if (quietHoursStart == null) return false;
        if (quietHoursStart.equals(quietHoursEnd)) return true;
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return !time.isBefore(quietHoursStart) && time.isBefore(quietHoursEnd);
        }
        return !time.isBefore(quietHoursStart) || time.isBefore(quietHoursEnd);
    }
}
