package com.erudit.notification.repository;

import com.erudit.notification.model.NotificationChannel;
import com.erudit.notification.model.NotificationDeliveryStatus;
import com.erudit.notification.model.NotificationType;
import com.erudit.notification.model.UserNotification;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository;
import java.sql.*; import java.time.*; import java.util.*;
@Repository
public class UserNotificationRepository {
 private final JdbcTemplate jdbc; public UserNotificationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void insert(UserNotification n){jdbc.update("INSERT INTO notifications(id,user_id,notification_type,title,body,created_at) VALUES (?,?,?,?,?,?)",n.id(),n.userId(),n.type().name(),n.title(),n.body(),Timestamp.from(n.createdAt()));}
 public List<UserNotification> list(UUID user,int page,int size){return jdbc.query("SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",this::map,user,size,page*size);}
 public long count(UUID user){Long v=jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=?",Long.class,user);return v==null?0:v;}
 public long unread(UUID user){Long v=jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND read_at IS NULL",Long.class,user);return v==null?0:v;}
 public boolean markRead(UUID id,UUID user,Instant at){return jdbc.update("UPDATE notifications SET read_at=? WHERE id=? AND user_id=? AND read_at IS NULL",Timestamp.from(at),id,user)>0;}
 public int markAllRead(UUID user,Instant at){return jdbc.update("UPDATE notifications SET read_at=? WHERE user_id=? AND read_at IS NULL",Timestamp.from(at),user);}
 public Optional<UserNotification> find(UUID id,UUID user){return jdbc.query("SELECT * FROM notifications WHERE id=? AND user_id=?",this::map,id,user).stream().findFirst();}
 public UUID createDelivery(UUID nid,NotificationChannel c,Instant at){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO notification_deliveries(id,notification_id,channel,status,created_at,updated_at) VALUES(?,?,?,?,?,?)",id,nid,c.name(),NotificationDeliveryStatus.PENDING.name(),Timestamp.from(at),Timestamp.from(at));return id;}
 public void updateDelivery(UUID id,NotificationDeliveryStatus status,Instant at,String error){jdbc.update("UPDATE notification_deliveries SET status=?,error_message=?,updated_at=? WHERE id=?",status.name(),error,Timestamp.from(at),id);}
 public long countSince(UUID user,Instant since){Long v=jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND created_at>=?",Long.class,user,Timestamp.from(since));return v==null?0:v;}
 public boolean claim(UUID user,NotificationType type,String key,Instant at){try{return jdbc.update("INSERT INTO notification_dispatch_claims(user_id,notification_type,deduplication_key,claimed_at) VALUES(?,?,?,?)",user,type.name(),key,Timestamp.from(at))==1;}catch(org.springframework.dao.DuplicateKeyException e){return false;}}
 public void release(UUID user,NotificationType type,String key){jdbc.update("DELETE FROM notification_dispatch_claims WHERE user_id=? AND notification_type=? AND deduplication_key=?",user,type.name(),key);}
 private UserNotification map(ResultSet r,int row)throws SQLException{Timestamp read=r.getTimestamp("read_at");return new UserNotification(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),NotificationType.valueOf(r.getString("notification_type")),r.getString("title"),r.getString("body"),read==null?null:read.toInstant(),r.getTimestamp("created_at").toInstant());}
}


