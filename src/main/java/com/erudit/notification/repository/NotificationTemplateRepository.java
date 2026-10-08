package com.erudit.notification.repository;

import com.erudit.notification.model.NotificationChannel;
import com.erudit.notification.model.NotificationTemplate;
import com.erudit.notification.model.NotificationType;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class NotificationTemplateRepository {
    private final JdbcTemplate jdbc;

    public NotificationTemplateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(NotificationTemplate value, Instant updatedAt) {
        jdbc.update("INSERT INTO notification_templates "
                        + "(id, notification_type, channel, title, body, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                value.id(), value.type().name(), value.channel().name(), value.title(), value.body(),
                Timestamp.from(updatedAt));
        saveParameters(value);
    }

    public boolean update(NotificationTemplate value, Instant updatedAt) {
        int updated = jdbc.update("UPDATE notification_templates SET notification_type = ?, channel = ?, "
                        + "title = ?, body = ?, updated_at = ? WHERE id = ?",
                value.type().name(), value.channel().name(), value.title(), value.body(),
                Timestamp.from(updatedAt), value.id());
        if (updated == 1) {
            jdbc.update("DELETE FROM notification_template_parameters WHERE template_id = ?", value.id());
            saveParameters(value);
        }
        return updated == 1;
    }

    public Optional<NotificationTemplate> find(UUID id) {
        return jdbc.query("SELECT * FROM notification_templates WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<NotificationTemplate> find(NotificationType type, NotificationChannel channel) {
        return jdbc.query("SELECT * FROM notification_templates WHERE notification_type = ? AND channel = ?",
                this::map, type.name(), channel.name()).stream().findFirst();
    }

    public List<NotificationTemplate> list(int page, int size) {
        return jdbc.query("SELECT * FROM notification_templates ORDER BY notification_type, channel, id "
                + "LIMIT ? OFFSET ?", this::map, size, page * size);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM notification_templates", Long.class);
        return count == null ? 0 : count;
    }

    private void saveParameters(NotificationTemplate value) {
        value.parameters().forEach(parameter -> jdbc.update(
                "INSERT INTO notification_template_parameters (template_id, parameter_name) VALUES (?, ?)",
                value.id(), parameter));
    }

    private NotificationTemplate map(ResultSet rs, int row) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        Set<String> parameters = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT parameter_name FROM notification_template_parameters WHERE template_id = ? "
                        + "ORDER BY parameter_name", String.class, id));
        return new NotificationTemplate(id, NotificationType.valueOf(rs.getString("notification_type")),
                NotificationChannel.valueOf(rs.getString("channel")), rs.getString("title"),
                rs.getString("body"), Set.copyOf(parameters));
    }
}
