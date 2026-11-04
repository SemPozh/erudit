package com.erudit.notificator.repository;

import com.erudit.notificator.model.NotificationRecord;
import com.erudit.notificator.model.Preference;
import com.erudit.notificator.model.Template;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {
    private final JdbcClient jdbc;
    public Preference preference(UUID user) {
        return jdbc.sql("SELECT * FROM notification_preferences WHERE user_id=:user").param("user", user)
                .query((rs,row)->new Preference(user,rs.getBoolean("enabled"),Arrays.asList(rs.getString("channels").split(","))))
                .optional().orElse(new Preference(user,true,List.of("IN_APP")));
    }
    public Preference save(Preference value) {
        jdbc.sql("""
                INSERT INTO notification_preferences(user_id,enabled,channels) VALUES(:user,:enabled,:channels)
                ON CONFLICT(user_id) DO UPDATE SET enabled=excluded.enabled,channels=excluded.channels""")
                .param("user",value.userId()).param("enabled",value.enabled()).param("channels",String.join(",",value.channels())).update();
        return value;
    }
    public NotificationRecord insert(UUID user,String code,String title,String body) {
        var value=new NotificationRecord(UUID.randomUUID(),user,code,title,body,false,OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.sql("INSERT INTO notifications(id,user_id,template_code,title,body,created_at) VALUES(:id,:user,:code,:title,:body,:created)")
                .param("id",value.id()).param("user",user).param("code",code).param("title",title).param("body",body)
                .param("created",Timestamp.from(value.createdAt().toInstant())).update(); return value;
    }
    public List<NotificationRecord> list(UUID user,int page,int size) {
        return jdbc.sql("SELECT * FROM notifications WHERE user_id=:user ORDER BY created_at DESC,id DESC LIMIT :size OFFSET :offset")
                .param("user",user).param("size",size).param("offset",page*size).query(this::notification).list();
    }
    public long count(UUID user) { return jdbc.sql("SELECT count(*) FROM notifications WHERE user_id=:user").param("user",user).query(Long.class).single(); }
    public long unread(UUID user) { return jdbc.sql("SELECT count(*) FROM notifications WHERE user_id=:user AND read_at IS NULL").param("user",user).query(Long.class).single(); }
    public boolean read(UUID user,UUID id) { return jdbc.sql("UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=:id AND user_id=:user").param("id",id).param("user",user).update()==1; }
    public void readAll(UUID user) { jdbc.sql("UPDATE notifications SET read_at=now() WHERE user_id=:user AND read_at IS NULL").param("user",user).update(); }
    public List<Template> templates() { return jdbc.sql("SELECT * FROM notification_templates ORDER BY code").query(this::mapTemplate).list(); }
    public Optional<Template> template(String code) { return jdbc.sql("SELECT * FROM notification_templates WHERE code=:code AND enabled").param("code",code).query(this::mapTemplate).optional(); }
    public Optional<Template> template(UUID id) { return jdbc.sql("SELECT * FROM notification_templates WHERE id=:id").param("id",id).query(this::mapTemplate).optional(); }
    public Template save(Template value) { jdbc.sql("INSERT INTO notification_templates(id,code,title_template,body_template,enabled) VALUES(:id,:code,:title,:body,:enabled)")
            .param("id",value.id()).param("code",value.code()).param("title",value.title()).param("body",value.body()).param("enabled",value.enabled()).update(); return value; }
    public Template update(Template value) { int rows=jdbc.sql("UPDATE notification_templates SET code=:code,title_template=:title,body_template=:body,enabled=:enabled WHERE id=:id")
            .param("id",value.id()).param("code",value.code()).param("title",value.title()).param("body",value.body()).param("enabled",value.enabled()).update();if(rows==0)throw new IllegalArgumentException("Template not found");return value; }
    public void deleteTemplate(UUID id){if(jdbc.sql("DELETE FROM notification_templates WHERE id=:id").param("id",id).update()==0)throw new IllegalArgumentException("Template not found");}
    private NotificationRecord notification(java.sql.ResultSet rs,int row)throws java.sql.SQLException{return new NotificationRecord(rs.getObject("id",UUID.class),rs.getObject("user_id",UUID.class),rs.getString("template_code"),rs.getString("title"),rs.getString("body"),rs.getTimestamp("read_at")!=null,rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC));}
    private Template mapTemplate(java.sql.ResultSet rs,int row)throws java.sql.SQLException{return new Template(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("title_template"),rs.getString("body_template"),rs.getBoolean("enabled"));}
}
