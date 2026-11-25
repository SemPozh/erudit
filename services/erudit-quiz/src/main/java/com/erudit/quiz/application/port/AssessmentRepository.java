package com.erudit.quiz.application.port;

import com.erudit.quiz.domain.*;
import com.erudit.quiz.domain.Assessment.*;
import java.time.OffsetDateTime;
import java.util.*;

public interface AssessmentRepository {
    List<Question> questions();
    Session start(UUID user);
    Optional<Session> session(UUID id);
    Session finish(Session value);
    Program saveProgram(Program value);
    Optional<Program> program(UUID user);
}
