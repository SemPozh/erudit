package com.erudit.friend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class FriendRepository {
    private final JdbcTemplate jdbc;
    public FriendRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean userExists(UUID id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }
    public boolean relationExists(UUID first, UUID second) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM friend_requests
            WHERE (requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?)
            """, Integer.class, first, second, second, first);
        return count != null && count > 0;
    }
    public UUID create(UUID requester, UUID addressee, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO friend_requests VALUES (?, ?, ?, 'PENDING', ?, ?)",
                id, requester, addressee, Timestamp.from(now), Timestamp.from(now));
        return id;
    }
    public boolean resolve(UUID id, UUID addressee, String status, Instant now) {
        return jdbc.update("""
            UPDATE friend_requests SET status = ?, updated_at = ?
            WHERE id = ? AND addressee_id = ? AND status = 'PENDING'
            """, status, Timestamp.from(now), id, addressee) == 1;
    }
    public List<FriendRecord> incoming(UUID user, int page, int size) {
        return jdbc.query("""
            SELECT fr.id, u.name FROM friend_requests fr JOIN users u ON u.id = fr.requester_id
            WHERE fr.addressee_id = ? AND fr.status = 'PENDING'
            ORDER BY fr.created_at, fr.id LIMIT ? OFFSET ?
            """, (rs, n) -> new FriendRecord(rs.getObject("id", UUID.class), rs.getString("name"), "PENDING"),
                user, size, page * size);
    }
    public long incomingCount(UUID user) {
        return jdbc.queryForObject("SELECT count(*) FROM friend_requests WHERE addressee_id=? AND status='PENDING'", Long.class, user);
    }
    public List<FriendRecord> friends(UUID user, int page, int size) {
        return jdbc.query("""
            SELECT u.id, u.name FROM friend_requests fr JOIN users u
              ON u.id = CASE WHEN fr.requester_id = ? THEN fr.addressee_id ELSE fr.requester_id END
            WHERE fr.status='ACCEPTED' AND (fr.requester_id=? OR fr.addressee_id=?)
            ORDER BY lower(u.name), u.id LIMIT ? OFFSET ?
            """, (rs, n) -> new FriendRecord(rs.getObject("id", UUID.class), rs.getString("name"), "FRIEND"),
                user, user, user, size, page * size);
    }
    public long friendsCount(UUID user) {
        return jdbc.queryForObject("SELECT count(*) FROM friend_requests WHERE status='ACCEPTED' AND (requester_id=? OR addressee_id=?)", Long.class, user, user);
    }
    public boolean areFriends(UUID first, UUID second) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM friend_requests WHERE status='ACCEPTED'
            AND ((requester_id=? AND addressee_id=?) OR (requester_id=? AND addressee_id=?))
            """, Integer.class, first, second, second, first);
        return count != null && count > 0;
    }
    public boolean remove(UUID first, UUID second) {
        return jdbc.update("""
            DELETE FROM friend_requests WHERE status='ACCEPTED'
            AND ((requester_id=? AND addressee_id=?) OR (requester_id=? AND addressee_id=?))
            """, first, second, second, first) == 1;
    }
}
