package com.erudit.quiz.application;

import com.erudit.quiz.application.port.CompetitionParticipants;
import com.erudit.quiz.domain.CompetitionModels.*;
import com.erudit.quiz.application.port.CompetitionRepository;

import java.util.*;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CompetitionService {
    private final CompetitionRepository repository;
    private final CompetitionParticipants participants;

    public Competition create(String title, UUID owner) {
        return repository.create(title, owner);
    }

    public void invite(UUID competition, UUID user, UUID actor) {
        if (!participants.active(user))
            throw new IllegalStateException("User service unavailable");
        repository.invite(competition, user, actor);
        participants.invited(competition, user);
    }

    public void join(UUID competition, UUID user) {
        repository.join(competition, user);
    }

    public List<Rating> leaderboard(UUID id) {
        return repository.leaderboard(id);
    }

    public List<Rating> ratings(String period) {
        return repository.ratings(period);
    }
}
