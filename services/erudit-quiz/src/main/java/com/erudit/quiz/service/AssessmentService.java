package com.erudit.quiz.service;
import com.erudit.quiz.model.Assessment.*;
import com.erudit.quiz.repository.AssessmentRepository;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class AssessmentService {
 private final AssessmentRepository repository;
 public List<Question> questions(){return repository.questions();}
 @Transactional public Session start(UUID user){return repository.start(user==null?UUID.fromString("00000000-0000-0000-0000-000000000001"):user);}
 @Transactional public Session submit(UUID id,List<Answer> answers){Session session=get(id);if(!"STARTED".equals(session.status()))throw new IllegalStateException("Assessment already submitted");Map<UUID,Question> questions=repository.questions().stream().collect(Collectors.toMap(Question::id,Function.identity()));int score=(int)answers.stream().filter(a->questions.containsKey(a.questionId())&&questions.get(a.questionId()).correctIndex()==a.optionIndex()).count();String grade=score*2>=questions.size()?"ADVANCED":"BEGINNER";Session result=new Session(id,session.userId(),"COMPLETED",score,grade);repository.finish(result);repository.saveProgram(new Program(session.userId(),grade,0));return result;}
 public Session get(UUID id){return repository.session(id).orElseThrow(()->new IllegalArgumentException("Assessment not found"));}
 public Program program(UUID user){return repository.program(user).orElseThrow(()->new IllegalArgumentException("Complete assessment first"));}
 public Step next(UUID user){Program p=program(user);return p.progress()==0?new Step("BASICS","Start with fundamentals"):new Step("PRACTICE","Continue practice");}
 public record Step(String code,String title) {}
}
