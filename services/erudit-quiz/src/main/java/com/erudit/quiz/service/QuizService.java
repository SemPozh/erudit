package com.erudit.quiz.service;import com.erudit.quiz.model.QuizModels.*;import com.erudit.quiz.repository.QuizRepository;import java.util.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor public class QuizService {private final QuizRepository repository;
 public List<Quiz> list(){return repository.list();} public Quiz daily(){return repository.daily();}
 @Transactional public Quiz create(String title,String prompt,List<String> options,int correct,String hint){if(correct>=options.size())throw new IllegalArgumentException("Correct option is out of range");return repository.create(title,prompt,options,correct,hint);}
 @Transactional public Attempt start(UUID quiz,UUID user){return repository.start(quiz,user==null?UUID.fromString("00000000-0000-0000-0000-000000000001"):user);}
 @Transactional public Hint hint(UUID id){Attempt v=repository.hint(repository.attempt(id));return new Hint(repository.question(v.quizId()).hint(),2-v.hintsUsed());}
 @Transactional public Attempt submit(UUID id,int option){Attempt v=repository.attempt(id);if(!"STARTED".equals(v.status()))throw new IllegalStateException("Quiz already submitted");return repository.finish(v,repository.question(v.quizId()).correct()==option?1:0);}
 public record Hint(String text,int remaining){}
}
