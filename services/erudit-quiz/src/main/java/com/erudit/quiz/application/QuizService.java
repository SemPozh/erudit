package com.erudit.quiz.application;

import com.erudit.quiz.domain.QuizModels.*;
import com.erudit.quiz.application.port.QuizRepository;
import com.erudit.quiz.application.port.QuizGeneration;
import java.util.*;

public class QuizService {
 private final QuizRepository repository;
 private final QuizGeneration ai;
 public QuizService(QuizRepository repository) { this(repository, (title, prompt) -> null); }
 public QuizService(QuizRepository repository, QuizGeneration ai) { this.repository=repository; this.ai=ai; }
 public List<Quiz> list(){return repository.list();} public Quiz daily(){return repository.daily();}
 public Quiz create(String title,String prompt,List<String> options,int correct,String hint){{var generated=ai.generate(title,prompt);if(generated!=null&&generated.question()!=null&&!generated.question().isBlank()&&generated.options()!=null&&generated.options().size()>=2&&generated.options().stream().allMatch(o->o!=null&&!o.isBlank())&&generated.correctOption()>=0&&generated.correctOption()<generated.options().size()){prompt=generated.question();options=generated.options();correct=generated.correctOption();}}if(options==null||correct<0||correct>=options.size())throw new IllegalArgumentException("Correct option is out of range");return repository.create(title,prompt,options,correct,hint);}
 public Attempt start(UUID quiz,UUID user){return repository.start(quiz,user==null?UUID.fromString("00000000-0000-0000-0000-000000000001"):user);}
 public Hint hint(UUID id){Attempt v=repository.hint(repository.attempt(id));return new Hint(repository.question(v.quizId()).hint(),2-v.hintsUsed());}
 public Attempt submit(UUID id,int option){Attempt v=repository.attempt(id);if(!"STARTED".equals(v.status()))throw new IllegalStateException("Quiz already submitted");return repository.finish(v,repository.question(v.quizId()).correct()==option?1:0);}
 public record Hint(String text,int remaining){}
}
