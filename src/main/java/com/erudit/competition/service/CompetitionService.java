package com.erudit.competition.service;

import com.erudit.competition.model.Competition;
import com.erudit.competition.repository.CompetitionRepository;
import com.erudit.notification.model.NotificationType;
import com.erudit.notification.service.UserNotificationService;
import com.erudit.quiz.model.Quiz;

import com.erudit.quiz.repository.QuizRepository;
import com.erudit.rating.service.RatingService;
import com.erudit.web.exception.NotFoundException;
import com.erudit.web.exception.ValidationException;
import com.erudit.web.exception.ForbiddenException;
import com.erudit.friend.service.FriendService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

@Service
public class CompetitionService {

    public record Details(Competition competition, String status, List<String> participantIds) {}
    public record Row(String userId, int score, int rank) {}
    public record LeaderboardPage(List<Row> rows, long total) {}

    private final CompetitionRepository repository;
    private final QuizRepository quizRepository;
    private final Clock clock;
    private final RatingService ratingService;
    private final FriendService friends;
    private final ObjectProvider<UserNotificationService> notifications;

    public CompetitionService(CompetitionRepository repository,
                              QuizRepository quizRepository,
                              Clock clock,
                              RatingService ratingService, FriendService friends,
                              ObjectProvider<UserNotificationService> notifications) {
        this.repository = repository;
        this.quizRepository = quizRepository;
        this.clock = clock;
        this.ratingService = ratingService;
        this.friends = friends;
        this.notifications = notifications;
    }

    @Transactional
    public Details addParticipant(String username, UUID id, UUID target) {
        Competition competition = editableByCreator(username, id);
        if (repository.participantCount(id) >= 100 && !repository.isParticipant(id, target.toString())) {
            throw new ValidationException("Competition participant limit reached");
        }
        repository.addParticipantIfAbsent(id, target.toString(), clock.instant());
        return details(competition);
    }

    @Transactional
    public void invite(String username, UUID id, UUID target) {
        Competition competition = editableByCreator(username, id);
        UUID creator;
        try { creator = UUID.fromString(username); } catch (IllegalArgumentException e) { throw new ForbiddenException("Creator identity is invalid"); }
        if (!friends.areFriends(creator, target)) throw new ForbiddenException("Only friends can be invited");
        if (repository.inviteIfAbsent(id, target, clock.instant())) {
            Runnable notify = () -> {
                UserNotificationService notifier = notifications.getIfAvailable();
                if (notifier != null) try { notifier.send(target, NotificationType.SOCIAL,
                        Map.of("competition", competition.title()), ZoneId.of("UTC")); }
                catch (RuntimeException ignored) { }
            };
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { notify.run(); }
                });
            } else notify.run();
        }
    }

    private Competition editableByCreator(String username, UUID id) {
        Competition competition = repository.findById(id).orElseThrow(() -> new NotFoundException("Competition not found"));
        if (!competition.creatorId().equals(username)) throw new ForbiddenException("Only the creator can change participants");
        if (!clock.instant().isBefore(competition.endsAt())) throw new ValidationException("Competition is closed");
        return competition;
    }

    @Transactional
    public Details create(String username, String title, UUID quizId, String rules,
                          Instant startsAt, Instant endsAt) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("Competition title is required");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ValidationException("Competition must end after it starts");
        }
        if (!endsAt.isAfter(clock.instant())) {
            throw new ValidationException("Competition cannot already be over");
        }
        if (quizRepository.findById(quizId).isEmpty()) {
            throw new ValidationException("Quiz not found");
        }

        var competition = new Competition(UUID.randomUUID(), title.trim(), quizId, username,
                rules == null || rules.isBlank() ? null : rules.trim(),
                startsAt, endsAt, clock.instant());
        repository.create(competition);
        return details(competition);
    }

    public Details get(String username, UUID id) {
        return details(accessible(username, id));
    }

    /**
     * Rules: a participant's result is their best submitted attempt on the quiz inside the
     * competition window (higher score first, then shorter time). Equal score and time share
     * a rank; participants without a result score 0 and share the last rank.
     */
    public LeaderboardPage leaderboard(String username, UUID id, int page, int size) {
        Competition competition = accessible(username, id);

        Map<String, CompetitionRepository.AttemptResult> best = new HashMap<>();
        for (var r : repository.results(id)) {
            best.merge(r.userId(), r, CompetitionService::better);
        }

        List<String> participants = repository.participantIds(id);
        List<String> ranked = participants.stream()
                .filter(best::containsKey)
                .sorted(Comparator.<String>comparingInt(u -> -best.get(u).score())
                        .thenComparing(u -> best.get(u).duration()))
                .toList();

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            var current = best.get(ranked.get(i));
            int rank = i + 1;
            if (i > 0) {
                var previous = best.get(ranked.get(i - 1));
                if (previous.score() == current.score()
                        && previous.duration().equals(current.duration())) {
                    rank = rows.get(i - 1).rank();
                }
            }
            rows.add(new Row(ranked.get(i), current.score(), rank));
        }
        int lastRank = ranked.size() + 1;
        participants.stream()
                .filter(u -> !best.containsKey(u))
                .forEach(u -> rows.add(new Row(u, 0, lastRank)));

        if (!clock.instant().isBefore(competition.endsAt())) {
            rows.forEach(row -> ratingService.recordCompetitionResult(row.userId(), competition.id(),
                    competition.quizId(), row.score(), row.rank(), rows.size()));
        }

        int from = (int) Math.min((long) page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        return new LeaderboardPage(List.copyOf(rows.subList(from, to)), rows.size());
    }

    private static CompetitionRepository.AttemptResult better(
            CompetitionRepository.AttemptResult a, CompetitionRepository.AttemptResult b) {
        if (a.score() != b.score()) {
            return a.score() > b.score() ? a : b;
        }
        return a.duration().compareTo(b.duration()) <= 0 ? a : b;
    }

    /** Only participants (the creator is one) may see a competition; everyone else gets 404. */
    private Competition accessible(String username, UUID id) {
        Competition competition = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Competition not found"));
        if (!repository.isParticipant(id, username)) {
            throw new NotFoundException("Competition not found");
        }
        return competition;
    }

    private Details details(Competition c) {
        Instant now = clock.instant();
        String status = now.isBefore(c.startsAt()) ? "SCHEDULED"
                : now.isAfter(c.endsAt()) ? "FINISHED" : "ACTIVE";
        return new Details(c, status, repository.participantIds(c.id()));
    }
}
