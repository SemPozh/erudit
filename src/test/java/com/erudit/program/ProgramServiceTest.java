package com.erudit.program;

import com.erudit.assessment.AssessmentFeedback;
import com.erudit.assessment.AssessmentService;
import com.erudit.assessment.AssessmentSubmission;
import com.erudit.assessment.Grade;
import com.erudit.assessment.TopicAssessmentScore;
import com.erudit.content.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.beans.factory.ObjectProvider;
import com.erudit.user.service.UserPreferenceProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProgramServiceTest {
    private final ProgramRepository repository = mock(ProgramRepository.class);
    private final AssessmentService assessmentService = mock(AssessmentService.class);
    private final ProgramPreferenceService preferenceService = mock(ProgramPreferenceService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<UserPreferenceProvider> userPreferences = mock(ObjectProvider.class);
    private final ProgramService service = new ProgramService(repository, assessmentService,
            preferenceService, userPreferences,
            Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void ordersWeakTopicsFirstAndMatchesDifficulty() {
        var cultureBeginner = candidate("CULTURE", Difficulty.BEGINNER);
        var cultureAdvanced = candidate("CULTURE", Difficulty.ADVANCED);
        var scienceBeginner = candidate("SCIENCE", Difficulty.BEGINNER);
        var scienceAdvanced = candidate("SCIENCE", Difficulty.ADVANCED);
        when(assessmentService.latestResult("student")).thenReturn(assessment(List.of(
                new TopicAssessmentScore("SCIENCE", 1, 1, 2000),
                new TopicAssessmentScore("CULTURE", 0, 1, 0))));
        when(preferenceService.aggregate(eq("student"), any())).thenReturn(new ProgramPreferences(Map.of()));
        when(repository.findCandidates()).thenReturn(List.of(
                cultureAdvanced, scienceBeginner, cultureBeginner, scienceAdvanced));
        AtomicReference<LearningProgram> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return null;
        }).when(repository).replace(eq("student"), any(LearningProgram.class));
        when(repository.findByUserId("student")).thenAnswer(ignored -> Optional.of(stored.get()));

        LearningProgram result = service.generate("student");

        assertThat(result.modules()).extracting(ProgramModule::topic)
                .containsExactly("CULTURE", "SCIENCE");
        assertThat(result.modules().get(0).lessons().getFirst().contentId())
                .isEqualTo(cultureBeginner.contentId());
        assertThat(result.modules().get(1).lessons().getFirst().contentId())
                .isEqualTo(scienceAdvanced.contentId());
    }

    private ProgramCandidate candidate(String topic, Difficulty difficulty) {
        return new ProgramCandidate(UUID.randomUUID(), UUID.randomUUID(), topic, difficulty);
    }

    private AssessmentSubmission assessment(List<TopicAssessmentScore> topics) {
        return new AssessmentSubmission(UUID.randomUUID(), 1, 2, 1000, topics,
                new Grade("ERUDITE_I", "Эрудит I", 900, 1049),
                new AssessmentFeedback("Feedback", List.of("Recommendation")));
    }
}
