package com.erudit.user.repository;

import com.erudit.user.model.User;
import com.erudit.user.model.UserSettings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class UserRepository {
    private final JdbcClient jdbc;
    public User create(String email,String name,String token,OffsetDateTime expires){UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO users(id,email,display_name,verification_token,verification_expires_at) VALUES(:id,:email,:name,:token,:expires)").param("id",id).param("email",email).param("name",name).param("token",token).param("expires",expires).update();jdbc.sql("INSERT INTO user_settings(user_id) VALUES(:id)").param("id",id).update();return get(id).orElseThrow();}
    public Optional<User> get(UUID id){return jdbc.sql("SELECT * FROM users WHERE id=:id").param("id",id).query(this::map).optional();}
    public List<User> search(String query,int page,int size){return jdbc.sql("SELECT * FROM users WHERE lower(display_name) LIKE lower(:query) ORDER BY created_at,id LIMIT :size OFFSET :offset").param("query","%"+(query==null?"":query)+"%").param("size",size).param("offset",page*size).query(this::map).list();}
    public long count(String query){return jdbc.sql("SELECT count(*) FROM users WHERE lower(display_name) LIKE lower(:query)").param("query","%"+(query==null?"":query)+"%").query(Long.class).single();}
    public boolean verify(String token){return jdbc.sql("UPDATE users SET verified=TRUE,verification_token=NULL,verification_expires_at=NULL WHERE verification_token=:token AND verification_expires_at>now()").param("token",token).update()==1;}
    public User update(UUID id,String name){if(jdbc.sql("UPDATE users SET display_name=:name WHERE id=:id").param("name",name).param("id",id).update()!=1)throw new IllegalArgumentException("User not found");return get(id).orElseThrow();}
    public void delete(UUID id){if(jdbc.sql("DELETE FROM users WHERE id=:id").param("id",id).update()!=1)throw new IllegalArgumentException("User not found");}
    public UserSettings settings(UUID id){return jdbc.sql("SELECT * FROM user_settings WHERE user_id=:id").param("id",id).query((rs,row)->new UserSettings(id,rs.getString("locale"),rs.getInt("daily_goal"))).optional().orElseThrow(()->new IllegalArgumentException("User not found"));}
    public UserSettings save(UserSettings v){jdbc.sql("INSERT INTO user_settings(user_id,locale,daily_goal) VALUES(:id,:locale,:goal) ON CONFLICT(user_id) DO UPDATE SET locale=excluded.locale,daily_goal=excluded.daily_goal").param("id",v.userId()).param("locale",v.locale()).param("goal",v.dailyGoal()).update();return v;}

    public boolean blocked(UUID id){return jdbc.sql("SELECT EXISTS(SELECT 1 FROM user_restrictions WHERE user_id=:id AND blocked)").param("id",id).query(Boolean.class).single();}
    public boolean block(UUID id){return jdbc.sql("INSERT INTO user_restrictions(user_id,blocked) SELECT id,TRUE FROM users WHERE id=:id ON CONFLICT(user_id) DO UPDATE SET blocked=TRUE").param("id",id).update()==1;}
    public List<com.erudit.user.model.AdminUser> adminSearch(String query,int page,int size){
        return jdbc.sql("SELECT u.*,COALESCE(r.blocked,FALSE) blocked FROM users u LEFT JOIN user_restrictions r ON r.user_id=u.id WHERE lower(u.display_name) LIKE lower(:query) OR lower(u.email) LIKE lower(:query) ORDER BY u.created_at,u.id LIMIT :size OFFSET :offset")
                .param("query","%"+(query==null?"":query.trim())+"%").param("size",size).param("offset",(long)page*size)
                .query((rs,row)->new com.erudit.user.model.AdminUser(rs.getObject("id",UUID.class),rs.getString("email"),rs.getString("display_name"),rs.getObject("created_at",OffsetDateTime.class),rs.getBoolean("blocked")?"BLOCKED":"ACTIVE")).list();
    }
    public long adminCount(String query){return jdbc.sql("SELECT count(*) FROM users WHERE lower(display_name) LIKE lower(:query) OR lower(email) LIKE lower(:query)")
            .param("query","%"+(query==null?"":query.trim())+"%").query(Long.class).single();}
    private User map(ResultSet rs,int row)throws SQLException{return new User(rs.getObject("id",UUID.class),rs.getString("email"),rs.getString("display_name"),rs.getBoolean("verified"),rs.getObject("created_at",OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC));}
}
