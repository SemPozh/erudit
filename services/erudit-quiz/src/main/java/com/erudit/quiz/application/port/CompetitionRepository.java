package com.erudit.quiz.application.port;

import com.erudit.quiz.domain.*;
import com.erudit.quiz.domain.CompetitionModels.*;
import java.time.OffsetDateTime;
import java.util.*;

public interface CompetitionRepository {
    Competition create(String title,UUID owner);
    void invite(UUID competition,UUID user,UUID actor);
    void join(UUID competition,UUID user);
    List<Rating> leaderboard(UUID competition);
    List<Rating> ratings(String period);
}
