package com.erudit.content.repository;

import com.erudit.content.model.ContentProgress;
import com.erudit.content.model.ContentReport;
import com.erudit.content.model.QuizCard;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ContentWorkflowRepository {
    private final JdbcClient jdbc;
    private final JdbcAggregateTemplate aggregates;
    public ContentWorkflowRepository(JdbcClient jdbc, JdbcAggregateTemplate aggregates) { this.jdbc=jdbc; this.aggregates=aggregates; }
    public Optional<ContentProgress> progress(UUID user, UUID id) {
        return jdbc.sql("SELECT * FROM content_progress WHERE user_id=:user AND content_id=:id")
                .param("user",user).param("id",id).query(ContentProgress.class).optional();
    }
    public ContentProgress record(UUID user, UUID id, boolean complete) {
        var now=OffsetDateTime.now(java.time.ZoneOffset.UTC);
        return jdbc.sql("""
                INSERT INTO content_progress(user_id,content_id,status,viewed_at,completed_at)
                VALUES(:user,:id,:status,:now,:completed)
                ON CONFLICT(user_id,content_id) DO UPDATE SET
                  status=CASE WHEN EXCLUDED.status='COMPLETED' THEN 'COMPLETED' ELSE content_progress.status END,
                  completed_at=COALESCE(content_progress.completed_at,
                    CASE WHEN EXCLUDED.completed_at IS NOT NULL THEN GREATEST(content_progress.viewed_at,EXCLUDED.completed_at) END)
                RETURNING *
                """).param("user",user).param("id",id).param("status",complete ? "COMPLETED" : "VIEWED")
                .param("now",now)
                .param("completed",complete ? now : null,java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .query(ContentProgress.class).single();
    }
    public List<QuizCard> cards(UUID id) {
        return jdbc.sql("SELECT * FROM content_quiz_cards WHERE content_id=:id ORDER BY position LIMIT 5").param("id",id)
                .query((rs,row) -> new QuizCard(rs.getObject("id",UUID.class),id,rs.getInt("position"),rs.getString("fact"),
                        rs.getString("question"),Arrays.asList((String[]) rs.getArray("answers").getArray()))).list();
    }
    public void replaceCards(UUID id, List<QuizCard> cards) {
        jdbc.sql("DELETE FROM content_quiz_cards WHERE content_id=:id").param("id",id).update();
        for (var c:cards) jdbc.sql("INSERT INTO content_quiz_cards(id,content_id,position,fact,question,answers) VALUES(:id,:content,:position,:fact,:question,:answers)")
                .param("id",c.id()).param("content",id).param("position",c.position()).param("fact",c.fact())
                .param("question",c.question()).param("answers",c.answers().toArray(String[]::new)).update();
    }
    public ContentReport report(UUID id, String reason) {
        return aggregates.insert(new ContentReport(UUID.randomUUID(),id,reason,"OPEN",OffsetDateTime.now(java.time.ZoneOffset.UTC)));
    }
    public Optional<ContentReport> report(UUID id) {
        return jdbc.sql("SELECT * FROM content_reports WHERE id=:id").param("id",id).query(ContentReport.class).optional();
    }
    public List<ContentReport> reports(int page,int size) {
        return jdbc.sql("SELECT * FROM content_reports WHERE status='OPEN' ORDER BY created_at,id LIMIT :size OFFSET :offset")
                .param("size",size).param("offset",(long)page*size).query(ContentReport.class).list();
    }
    public long countReports() { return jdbc.sql("SELECT count(*) FROM content_reports WHERE status='OPEN'").query(Long.class).single(); }
    public long countPending() { return jdbc.sql("SELECT count(*) FROM content WHERE status='PENDING_MODERATION'").query(Long.class).single(); }
    public boolean decide(UUID id,String status) {
        return jdbc.sql("UPDATE content SET status=:status,updated_at=now() WHERE id=:id AND status='PENDING_MODERATION'")
                .param("id",id).param("status",status).update()==1;
    }
    public boolean resolve(UUID id,String decision,String comment,UUID actor) {
        return jdbc.sql("UPDATE content_reports SET status='RESOLVED',decision=:decision,resolution_comment=:comment,resolved_at=now(),resolved_by=:actor WHERE id=:id AND status='OPEN'")
                .param("id",id).param("decision",decision).param("comment",comment).param("actor",actor).update()==1;
    }
    public void archivePublished(UUID id) {
        jdbc.sql("UPDATE content SET status='ARCHIVED',updated_at=now() WHERE id=:id AND status='PUBLISHED'").param("id",id).update();
    }
    public void audit(UUID content,UUID report,UUID actor,String action,String reason) {
        jdbc.sql("INSERT INTO content_moderation_audit(id,content_id,report_id,actor_id,action,reason) VALUES(:id,:content,:report,:actor,:action,:reason)")
                .param("id",UUID.randomUUID()).param("content",content).param("report",report).param("actor",actor)
                .param("action",action).param("reason",reason).update();
    }
}
