package com.erudit.quiz.application.port;

import com.erudit.quiz.domain.*;
import com.erudit.quiz.domain.QuizModels.*;
import java.time.OffsetDateTime;
import java.util.*;

public interface QuizRepository {
    List<Quiz> list();
    Quiz daily();
    Quiz create(String title,String prompt,List<String> options,int correct,String hint);
    Question question(UUID quiz);
    Attempt start(UUID quiz,UUID user);
    Attempt attempt(UUID id);
    Attempt hint(Attempt v);
    Attempt finish(Attempt v,int score);
}
