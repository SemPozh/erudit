package com.erudit.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.erudit.quiz.controller.CompetitionController;
import com.erudit.quiz.model.CompetitionModels.Competition;
import com.erudit.quiz.model.CompetitionModels.Rating;
import com.erudit.quiz.openapi.model.CompetitionRequest;
import com.erudit.quiz.openapi.model.InviteRequest;
import com.erudit.quiz.service.CompetitionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompetitionControllerTest {
    @Test
    void exposesCompetitionLifecycleAndRatings() {
        var service = mock(CompetitionService.class);
        var controller = new CompetitionController(service);
        UUID competitionId = UUID.randomUUID(), owner = UUID.randomUUID(), member = UUID.randomUUID();
        when(service.create("Demo", owner)).thenReturn(new Competition(competitionId, "Demo", owner, "OPEN"));
        when(service.leaderboard(competitionId)).thenReturn(List.of(new Rating(member, 42, 1)));
        when(service.ratings("ALL")).thenReturn(List.of(new Rating(owner, 100, 1)));

        var created = controller.createCompetition(new CompetitionRequest("Demo"), owner);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().getId()).isEqualTo(competitionId);
        assertThat(controller.inviteCompetition(competitionId, new InviteRequest(member), owner).getStatusCode().value())
                .isEqualTo(202);
        assertThat(controller.joinCompetition(competitionId, member).getStatusCode().value()).isEqualTo(204);
        assertThat(controller.competitionLeaderboard(competitionId).getBody()).hasSize(1);
        assertThat(controller.globalRatings("ALL").getBody()).hasSize(1);
        verify(service).invite(competitionId, member, owner);
        verify(service).join(competitionId, member);
    }
}
