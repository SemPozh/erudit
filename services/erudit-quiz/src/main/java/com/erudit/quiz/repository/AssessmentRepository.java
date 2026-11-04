package com.erudit.quiz.repository;
import com.erudit.quiz.model.Assessment.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository @RequiredArgsConstructor
public class AssessmentRepository {
 private final JdbcClient jdbc;
 public List<Question> questions(){return jdbc.sql("SELECT * FROM assessment_questions ORDER BY id").query((r,n)->new Question(r.getObject("id",UUID.class),r.getString("prompt"),List.of(r.getString("options").split("\\|")),r.getInt("correct_index"))).list();}
 public Session start(UUID user){var value=new Session(UUID.randomUUID(),user,"STARTED",null,null);jdbc.sql("INSERT INTO assessment_sessions(id,user_id,status) VALUES(:id,:user,'STARTED')").param("id",value.id()).param("user",user).update();return value;}
 public Optional<Session> session(UUID id){return jdbc.sql("SELECT * FROM assessment_sessions WHERE id=:id").param("id",id).query((r,n)->new Session(id,r.getObject("user_id",UUID.class),r.getString("status"),(Integer)r.getObject("score"),r.getString("grade"))).optional();}
 public Session finish(Session value){if(jdbc.sql("UPDATE assessment_sessions SET status='COMPLETED',score=:score,grade=:grade WHERE id=:id AND status='STARTED'").param("score",value.score()).param("grade",value.grade()).param("id",value.id()).update()!=1)throw new IllegalStateException("Assessment already submitted");return value;}
 public Program saveProgram(Program value){jdbc.sql("INSERT INTO learning_programs(user_id,grade,progress) VALUES(:user,:grade,:progress) ON CONFLICT(user_id) DO UPDATE SET grade=excluded.grade").param("user",value.userId()).param("grade",value.grade()).param("progress",value.progress()).update();return value;}
 public Optional<Program> program(UUID user){return jdbc.sql("SELECT * FROM learning_programs WHERE user_id=:user").param("user",user).query((r,n)->new Program(user,r.getString("grade"),r.getInt("progress"))).optional();}
}
