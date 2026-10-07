package com.erudit.competition;

import com.erudit.openapi.api.CompetitionsApi;
import com.erudit.openapi.model.CompetitionCreateRequest;
import com.erudit.openapi.model.CompetitionResponse;
import com.erudit.openapi.model.FriendRequestCreate;
import com.erudit.openapi.model.RatingListResponse;
import com.erudit.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
public class CompetitionController implements CompetitionsApi {   // check generated name

    private final CompetitionService service;
    private final HttpServletRequest request;

    public CompetitionController(CompetitionService service, HttpServletRequest request) {
        this.service = service;
        this.request = request;
    }

    @Override
    public ResponseEntity<CompetitionResponse> createCompetition(CompetitionCreateRequest body) {
        var details = service.create(currentUser(), body.getTitle(), body.getQuizId(),
                body.getRules(), body.getStartsAt().toInstant(), body.getEndsAt().toInstant());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(details));
    }

    @Override
    public ResponseEntity<CompetitionResponse> getCompetition(UUID id) {
        return ResponseEntity.ok(toResponse(service.get(currentUser(), id)));
    }

    @Override
    public ResponseEntity<RatingListResponse> getCompetitionLeaderboard(UUID id, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        var result = service.leaderboard(currentUser(), id, p, s);

        var entries = result.rows().stream().map(row -> {
            var entry = new com.erudit.openapi.model.RatingEntry();
            entry.setUserId(UUID.fromString(row.userId()));
            entry.setScore((double) row.score());
            entry.setRank(row.rank());
            return entry;
        }).toList();

        var meta = new com.erudit.openapi.model.PageMetadata();
        meta.setPage(p);
        meta.setSize(s);
        meta.setTotalElements(result.total());
        meta.setTotalPages((int) Math.ceil(result.total() / (double) s));

        var body = new RatingListResponse(entries);
        body.setPagination(meta);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Void> inviteToCompetition(UUID id, FriendRequestCreate friendRequestCreate) {
        return null;
    }

    private CompetitionResponse toResponse(CompetitionService.Details details) {
        Competition c = details.competition();
        var model = new com.erudit.openapi.model.Competition();
        model.setId(c.id());
        model.setTitle(c.title());
        model.setStatus(details.status());
        model.setQuizId(c.quizId());
        model.setCreatorId(c.creatorId());
        model.setRules(c.rules());
        model.setStartsAt(OffsetDateTime.ofInstant(c.startsAt(), ZoneOffset.UTC));
        model.setEndsAt(OffsetDateTime.ofInstant(c.endsAt(), ZoneOffset.UTC));
        model.setParticipantIds(details.participantIds());
        return new CompetitionResponse(model);
    }

    private String currentUser() {
        if (request.getUserPrincipal() == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        return request.getUserPrincipal().getName();
    }

    @Override
    public ResponseEntity<CompetitionResponse> addCompetitionParticipant(UUID id, FriendRequestCreate friendRequestCreate) {
        return null;
    }

}