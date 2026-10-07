package com.erudit.program;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramStreakTest {
    @Test
    void countsConsecutiveDaysAndAllowsTodayToBeMissing() {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        List<Instant> completions = List.of(
                Instant.parse("2026-10-05T12:00:00Z"),
                Instant.parse("2026-10-04T12:00:00Z"),
                Instant.parse("2026-10-03T12:00:00Z"),
                Instant.parse("2026-10-01T12:00:00Z"));

        assertThat(ProgramService.streak(completions, LocalDate.of(2026, 10, 6), zone)).isEqualTo(3);
    }
}

