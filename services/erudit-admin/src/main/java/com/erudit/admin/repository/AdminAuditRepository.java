package com.erudit.admin.repository;
import com.erudit.admin.model.AdminAudit;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class AdminAuditRepository {
    private final JdbcClient jdbc;
    private final JdbcAggregateTemplate aggregates;
    public AdminAuditRepository(JdbcClient jdbc,JdbcAggregateTemplate aggregates){this.jdbc=jdbc;this.aggregates=aggregates;}
    public void insert(AdminAudit value){aggregates.insert(value);}
    public void finish(UUID id,String status,String metadata){jdbc.sql("UPDATE admin_audit SET status=:status,metadata=:metadata,completed_at=now() WHERE id=:id")
            .param("id",id).param("status",status).param("metadata",metadata).update();}
    private JdbcClient.StatementSpec filtered(String select,UUID actor,String action,OffsetDateTime from,OffsetDateTime to) {
        String sql=select+" FROM admin_audit WHERE 1=1"+(actor==null?"":" AND actor_id=:actor")
                +(action==null?"":" AND action=:action")+(from==null?"":" AND occurred_at>=:from")+(to==null?"":" AND occurred_at<=:to");
        var query=jdbc.sql(sql);if(actor!=null)query=query.param("actor",actor);if(action!=null)query=query.param("action",action);
        if(from!=null)query=query.param("from",from);if(to!=null)query=query.param("to",to);return query;
    }
    public Page list(int page,int size,UUID actor,String action,OffsetDateTime from,OffsetDateTime to) {
        // SQL text is selected locally; all filter values are parameters.
        String where=" WHERE 1=1"+(actor==null?"":" AND actor_id=:actor")+(action==null?"":" AND action=:action")
                +(from==null?"":" AND occurred_at>=:from")+(to==null?"":" AND occurred_at<=:to");
        var query=jdbc.sql("SELECT * FROM admin_audit"+where+" ORDER BY occurred_at DESC,id LIMIT :size OFFSET :offset");
        if(actor!=null)query=query.param("actor",actor);if(action!=null)query=query.param("action",action);
        if(from!=null)query=query.param("from",from);if(to!=null)query=query.param("to",to);
        List<AdminAudit> rows=query.param("size",size).param("offset",(long)page*size).query(AdminAudit.class).list();
        long total=filtered("SELECT count(*)",actor,action,from,to).query(Long.class).single();
        return new Page(rows,total,page,size);
    }
    public record Page(List<AdminAudit> items,long total,int page,int size){}
}
