package com.erudit.program;

import com.erudit.content.Difficulty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProgramRepository {
    private final JdbcTemplate jdbc;

    public ProgramRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProgramCandidate> findCandidates() {
        return jdbc.query("""
                SELECT c.id content_id, q.id quiz_id, cat.name category, c.difficulty
                FROM content c
                JOIN categories cat ON cat.id = c.category_id
                JOIN quizzes q ON q.content_id = c.id
                WHERE c.status = 'PUBLISHED'
                ORDER BY cat.name, c.created_at, c.id, q.created_at, q.id
                """, (rs, row) -> new ProgramCandidate(rs.getObject("content_id", UUID.class),
                rs.getObject("quiz_id", UUID.class), rs.getString("category"),
                Difficulty.valueOf(rs.getString("difficulty"))));
    }

    @Transactional
    public void replace(String userId, LearningProgram program) {
        jdbc.update("DELETE FROM learning_programs WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO learning_programs (id, user_id, created_at) VALUES (?, ?, ?)",
                program.id(), userId, Timestamp.from(program.createdAt()));
        for (ProgramModule module : program.modules()) {
            jdbc.update("""
                    INSERT INTO program_modules (id, program_id, topic, title, position)
                    VALUES (?, ?, ?, ?, ?)
                    """, module.id(), program.id(), module.topic(), module.title(), module.position());
            for (ProgramLesson lesson : module.lessons()) {
                jdbc.update("""
                        INSERT INTO program_lessons (id, module_id, content_id, quiz_id, position)
                        VALUES (?, ?, ?, ?, ?)
                        """, lesson.id(), module.id(), lesson.contentId(), lesson.quizId(), lesson.position());
            }
        }
    }

    public Optional<LearningProgram> findByUserId(String userId) {
        return jdbc.query("SELECT id, created_at FROM learning_programs WHERE user_id = ?", (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            List<ProgramModule> modules = findModules(id, userId);
            return new LearningProgram(id, userId, rs.getTimestamp("created_at").toInstant(),
                    progress(modules.stream().flatMap(module -> module.lessons().stream()).toList()), modules);
        }, userId).stream().findFirst();
    }

    public List<Instant> completionTimes(String userId) {
        return jdbc.query("""
                SELECT completed_at FROM content_progress
                WHERE user_id = ? AND status = 'COMPLETED' AND completed_at IS NOT NULL
                ORDER BY completed_at DESC
                """, (rs, row) -> rs.getTimestamp("completed_at").toInstant(), userId);
    }

    public int completedMinutes(String userId, Instant from, Instant to) {
        Integer minutes = jdbc.queryForObject("""
                SELECT COALESCE(SUM(c.estimated_minutes), 0)
                FROM content_progress cp JOIN content c ON c.id = cp.content_id
                WHERE cp.user_id = ? AND cp.status = 'COMPLETED'
                  AND cp.completed_at >= ? AND cp.completed_at < ?
                """, Integer.class, userId, Timestamp.from(from), Timestamp.from(to));
        return minutes == null ? 0 : minutes;
    }

    private List<ProgramModule> findModules(UUID programId, String userId) {
        return jdbc.query("""
                SELECT id, topic, title, position FROM program_modules
                WHERE program_id = ? ORDER BY position
                """, (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            List<ProgramLesson> lessons = findLessons(id, userId);
            return new ProgramModule(id, rs.getString("topic"), rs.getString("title"), rs.getInt("position"),
                    progress(lessons), lessons);
        }, programId);
    }

    private List<ProgramLesson> findLessons(UUID moduleId, String userId) {
        return jdbc.query("""
                SELECT l.id, l.content_id, l.quiz_id, l.position,
                       CASE WHEN cp.status = 'COMPLETED' THEN 'COMPLETED' ELSE 'NOT_STARTED' END lesson_status
                FROM program_lessons l
                LEFT JOIN content_progress cp ON cp.content_id = l.content_id AND cp.user_id = ?
                WHERE l.module_id = ? ORDER BY l.position
                """, (rs, row) -> new ProgramLesson(rs.getObject("id", UUID.class),
                rs.getObject("content_id", UUID.class), rs.getObject("quiz_id", UUID.class),
                rs.getInt("position"), LessonStatus.valueOf(rs.getString("lesson_status"))), userId, moduleId);
    }

    private double progress(List<ProgramLesson> lessons) {
        if (lessons.isEmpty()) return 0;
        long completed = lessons.stream().filter(lesson -> lesson.status() == LessonStatus.COMPLETED).count();
        return Math.round(completed * 10_000.0 / lessons.size()) / 100.0;
    }
}
