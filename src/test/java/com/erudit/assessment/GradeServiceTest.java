package com.erudit.assessment;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GradeServiceTest {
    private final GradeRepository repository = mock(GradeRepository.class);
    private final GradeService service = new GradeService(repository);

    @Test
    void resolvesConfiguredGrade() {
        Grade expected = new Grade("ERUDITE_I", "Эрудит I", 900, 1049);
        when(repository.findByScore(1000)).thenReturn(Optional.of(expected));

        assertThat(service.resolve(1000)).isEqualTo(expected);
    }

    @Test
    void rejectsScoresOutsideTheRatingScale() {
        assertThatThrownBy(() -> service.resolve(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolve(2001)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsGapInConfiguredThresholds() {
        when(repository.findByScore(1000)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1000");
    }
}
