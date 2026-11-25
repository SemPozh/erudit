package com.erudit.quiz.application.port;

import java.util.UUID;

public interface CompetitionParticipants {
    boolean active(UUID user);
    void invited(UUID competition, UUID user);
}
