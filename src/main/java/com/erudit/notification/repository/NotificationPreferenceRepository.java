package com.erudit.notification.repository;

import com.erudit.notification.model.NotificationChannel;
import com.erudit.notification.model.NotificationPreference;
import com.erudit.notification.model.NotificationType;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class NotificationPreferenceRepository {
    private final JdbcTemplate jdbc;

    public NotificationPreferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<NotificationPreference> find(UUID userId) {
        return jdbc.query("SELECT * FROM notification_preferences WHERE user_id = ?", (rs, row) -> {
            Time start = rs.getTime("quiet_hours_start");
            Time end = rs.getTime("quiet_hours_end");
            Set<NotificationChannel> channels = new LinkedHashSet<>(jdbc.queryForList(
                    "SELECT channel FROM notification_preference_channels WHERE user_id = ? ORDER BY channel",
                    String.class, userId).stream().map(NotificationChannel::valueOf).toList());
            Set<NotificationType> types = new LinkedHashSet<>(jdbc.queryForList(
                    "SELECT notification_type FROM notification_preference_types WHERE user_id = ? ORDER BY notification_type",
                    String.class, userId).stream().map(NotificationType::valueOf).toList());
            return new NotificationPreference(userId, Set.copyOf(channels), Set.copyOf(types),
                    start == null ? null : start.toLocalTime(), end == null ? null : end.toLocalTime());
        }, userId).stream().findFirst();
    }

    public void save(NotificationPreference value, Instant updatedAt) {
        int updated = jdbc.update("UPDATE notification_preferences SET quiet_hours_start = ?, "
                        + "quiet_hours_end = ?, updated_at = ? WHERE user_id = ?",
                time(value.quietHoursStart()), time(value.quietHoursEnd()), Timestamp.from(updatedAt), value.userId());
        if (updated == 0) {
            jdbc.update("INSERT INTO notification_preferences "
                            + "(user_id, quiet_hours_start, quiet_hours_end, updated_at) VALUES (?, ?, ?, ?)",
                    value.userId(), time(value.quietHoursStart()), time(value.quietHoursEnd()), Timestamp.from(updatedAt));
        }
        jdbc.update("DELETE FROM notification_preference_channels WHERE user_id = ?", value.userId());
        value.channels().forEach(channel -> jdbc.update(
                "INSERT INTO notification_preference_channels (user_id, channel) VALUES (?, ?)",
                value.userId(), channel.name()));
        jdbc.update("DELETE FROM notification_preference_types WHERE user_id = ?", value.userId());
        value.types().forEach(type -> jdbc.update(
                "INSERT INTO notification_preference_types (user_id, notification_type) VALUES (?, ?)",
                value.userId(), type.name()));
    }

    private static Time time(LocalTime value) {
        return value == null ? null : Time.valueOf(value);
    }
}
