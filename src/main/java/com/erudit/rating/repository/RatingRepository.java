package com.erudit.rating.repository;

import com.erudit.rating.model.RatingProfile;
import com.erudit.rating.model.RatingRow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public class RatingRepository {
    private final JdbcTemplate jdbc;

    public RatingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RatingProfile> findProfile(String userId) {
        return jdbc.query("SELECT user_id, points, er_score, grade_code FROM user_rating_profiles WHERE user_id = ?",
                (rs, row) -> new RatingProfile(rs.getString("user_id"), rs.getLong("points"),
                        rs.getInt("er_score"), rs.getString("grade_code")), userId).stream().findFirst();
    }

    public int assessmentErScore(String userId) {
        return jdbc.query("SELECT er_score FROM assessment_profiles WHERE user_id = ?",
                (rs, row) -> rs.getInt(1), userId).stream().findFirst().orElse(0);
    }

    public boolean eventExists(String userId, String sourceType, UUID sourceId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM rating_events
                WHERE user_id = ? AND source_type = ? AND source_id = ?
                """, Integer.class, userId, sourceType, sourceId);
        return count != null && count > 0;
    }

    public UUID categoryForQuiz(UUID quizId) {
        return jdbc.query("""
                SELECT c.category_id FROM quizzes q JOIN content c ON c.id = q.content_id
                WHERE q.id = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), quizId).stream().findFirst().orElse(null);
    }

    public void insertEvent(String userId, String sourceType, UUID sourceId, UUID categoryId,
                            int points, int erDelta, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO rating_events
                    (id, user_id, source_type, source_id, category_id, points, er_delta, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, sourceType, sourceId, categoryId, points, erDelta,
                Timestamp.from(occurredAt));
    }

    public void saveProfile(RatingProfile profile, Instant updatedAt) {
        int updated = jdbc.update("""
                UPDATE user_rating_profiles
                SET points = ?, er_score = ?, grade_code = ?, updated_at = ?
                WHERE user_id = ?
                """, profile.points(), profile.erScore(), profile.gradeCode(), Timestamp.from(updatedAt),
                profile.userId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO user_rating_profiles (user_id, points, er_score, grade_code, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, profile.userId(), profile.points(), profile.erScore(), profile.gradeCode(),
                    Timestamp.from(updatedAt));
        }
    }

    public List<RatingRow> rating(UUID categoryId, Instant since, String viewerId, boolean friendsOnly, int page, int size) {
        String score = categoryId == null && since == null ? "p.points" : "COALESCE(sum(e.points),0)";
        String join = categoryId == null && since == null ? "" : " JOIN rating_events e ON e.user_id=p.user_id AND (?::uuid IS NULL OR e.category_id=?) AND (?::timestamptz IS NULL OR e.occurred_at>=?) ";
        String friendship = friendsOnly ? " AND (p.user_id=? OR EXISTS (SELECT 1 FROM friend_requests f WHERE f.status='ACCEPTED' AND ((f.requester_id::text=? AND f.addressee_id::text=p.user_id) OR (f.addressee_id::text=? AND f.requester_id::text=p.user_id))))" : "";
        String sql="SELECT p.user_id,"+score+" score,p.grade_code,dense_rank() OVER(ORDER BY "+score+" DESC) rank FROM user_rating_profiles p JOIN users u ON u.id::text=p.user_id LEFT JOIN user_settings s ON s.user_id=u.id"+join+" WHERE COALESCE(s.visible_in_rating,true)"+friendship+" GROUP BY p.user_id,p.points,p.grade_code ORDER BY score DESC,p.user_id LIMIT ? OFFSET ?";
        var args=new java.util.ArrayList<Object>(); if(!join.isEmpty()){args.add(categoryId);args.add(categoryId);args.add(since==null?null:Timestamp.from(since));args.add(since==null?null:Timestamp.from(since));} if(friendsOnly){args.add(viewerId);args.add(viewerId);args.add(viewerId);} args.add(size);args.add(page*size);
        return jdbc.query(sql,(rs,n)->new RatingRow(rs.getString("user_id"),rs.getLong("score"),rs.getInt("rank"),rs.getString("grade_code")),args.toArray());
    }

    public long ratingCount(UUID categoryId, Instant since, String viewerId, boolean friendsOnly) {
        String event=categoryId==null&&since==null?"":" AND EXISTS (SELECT 1 FROM rating_events e WHERE e.user_id=p.user_id AND (?::uuid IS NULL OR e.category_id=?) AND (?::timestamptz IS NULL OR e.occurred_at>=?))";
        String friendship=friendsOnly?" AND (p.user_id=? OR EXISTS (SELECT 1 FROM friend_requests f WHERE f.status='ACCEPTED' AND ((f.requester_id::text=? AND f.addressee_id::text=p.user_id) OR (f.addressee_id::text=? AND f.requester_id::text=p.user_id))))":"";
        var args=new java.util.ArrayList<Object>();if(!event.isEmpty()){args.add(categoryId);args.add(categoryId);args.add(since==null?null:Timestamp.from(since));args.add(since==null?null:Timestamp.from(since));}if(friendsOnly){args.add(viewerId);args.add(viewerId);args.add(viewerId);}
        return jdbc.queryForObject("SELECT count(*) FROM user_rating_profiles p JOIN users u ON u.id::text=p.user_id LEFT JOIN user_settings s ON s.user_id=u.id WHERE COALESCE(s.visible_in_rating,true)"+event+friendship,Long.class,args.toArray());
    }

    public List<String> friendsOvertaken(String userId,long before,long after) {
        return jdbc.query("""
            SELECT p.user_id FROM user_rating_profiles p JOIN friend_requests f
              ON f.status='ACCEPTED' AND ((f.requester_id::text=? AND f.addressee_id::text=p.user_id)
                 OR (f.addressee_id::text=? AND f.requester_id::text=p.user_id))
            WHERE p.points>? AND p.points<=?
            """,(rs,n)->rs.getString(1),userId,userId,before,after);
    }
}
