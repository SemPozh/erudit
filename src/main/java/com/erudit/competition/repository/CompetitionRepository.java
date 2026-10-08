package com.erudit.competition.repository;

import com.erudit.competition.model.Competition;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CompetitionRepository {

    /** A participant's submitted quiz attempt that falls inside the competition window. */
    public record AttemptResult(String userId, int score, Duration duration) {}

    private final JdbcTemplate jdbc;

    public CompetitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(Competition c) {
        jdbc.update("""
                INSERT INTO competitions
                    (id, title, quiz_id, creator_id, rules, starts_at, ends_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                c.id(), c.title(), c.quizId(), c.creatorId(), c.rules(),
                Timestamp.from(c.startsAt()), Timestamp.from(c.endsAt()),
                Timestamp.from(c.createdAt()));
        addParticipant(c.id(), c.creatorId(), c.createdAt());
    }

    public void addParticipant(UUID competitionId, String userId, Instant joinedAt) {
        jdbc.update("""
                INSERT INTO competition_participants (competition_id, user_id, joined_at)
                VALUES (?, ?, ?)
                """, competitionId, userId, Timestamp.from(joinedAt));
    }

    public void addParticipantIfAbsent(UUID competitionId, String userId, Instant joinedAt) {
        jdbc.update("""
                INSERT INTO competition_participants (competition_id, user_id, joined_at)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, competitionId, userId, Timestamp.from(joinedAt));
    }

    public int participantCount(UUID competitionId) {
        return jdbc.queryForObject("SELECT count(*) FROM competition_participants WHERE competition_id=?", Integer.class, competitionId);
    }

    public boolean inviteIfAbsent(UUID competitionId, UUID userId, Instant invitedAt) {
        return jdbc.update("""
                INSERT INTO competition_invitations (competition_id, user_id, invited_at)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, competitionId, userId, Timestamp.from(invitedAt)) == 1;
    }

    public Optional<Competition> findById(UUID id) {
        return jdbc.query("""
                SELECT id, title, quiz_id, creator_id, rules, starts_at, ends_at, created_at
                FROM competitions WHERE id = ?
                """,
                (rs, i) -> new Competition(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("quiz_id", UUID.class),
                        rs.getString("creator_id"),
                        rs.getString("rules"),
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()),
                id).stream().findFirst();
    }

    public List<String> participantIds(UUID competitionId) {
        return jdbc.query("""
                SELECT user_id FROM competition_participants
                WHERE competition_id = ? ORDER BY joined_at, user_id
                """,
                (rs, i) -> rs.getString("user_id"), competitionId);
    }

    public boolean isParticipant(UUID competitionId, String userId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM competition_participants
                WHERE competition_id = ? AND user_id = ?
                """, Integer.class, competitionId, userId);
        return n != null && n > 0;
    }

    /** Submitted attempts on the competition's quiz by participants, inside the window. */
    public List<AttemptResult> results(UUID competitionId) {
        return jdbc.query("""
                SELECT a.user_id, a.score, a.started_at, a.submitted_at
                FROM quiz_attempts a
                JOIN competitions c ON c.quiz_id = a.quiz_id
                JOIN competition_participants p
                     ON p.competition_id = c.id AND p.user_id = a.user_id
                WHERE c.id = ?
                  AND a.submitted_at IS NOT NULL
                  AND a.submitted_at >= c.starts_at
                  AND a.submitted_at <= c.ends_at
                """,
                (rs, i) -> new AttemptResult(
                        rs.getString("user_id"),
                        rs.getInt("score"),
                        Duration.between(rs.getTimestamp("started_at").toInstant(),
                                rs.getTimestamp("submitted_at").toInstant())),
                competitionId);
    }
}
