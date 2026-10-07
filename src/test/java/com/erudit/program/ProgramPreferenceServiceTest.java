package com.erudit.program;

import com.erudit.assessment.TopicAssessmentScore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProgramPreferenceServiceTest {
    private final ProgramPreferenceRepository repository = mock(ProgramPreferenceRepository.class);
    private final ProgramPreferenceService service = new ProgramPreferenceService(repository);

    @Test
    void combinesExplicitAssessmentAndBehavioralSignalsWithDocumentedPriority() {
        UUID userId = UUID.randomUUID();
        when(repository.favoriteTopics(userId)).thenReturn(Map.of("Наука", 100));
        when(repository.behavioralTopics(userId.toString())).thenReturn(Map.of("SCIENCE", 20, "CULTURE", 40));

        ProgramPreferences result = service.aggregate(userId.toString(), List.of(
                new TopicAssessmentScore("SCIENCE", 1, 1, 1600),
                new TopicAssessmentScore("CULTURE", 0, 1, 0)));

        assertThat(result.weight("SCIENCE")).isEqualTo(174);
        assertThat(result.weight("CULTURE")).isEqualTo(110);
    }
}

