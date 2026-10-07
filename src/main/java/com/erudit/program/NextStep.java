package com.erudit.program;

import java.util.UUID;

public record NextStep(UUID contentId, UUID quizId, String reason, int streakDays, double dailyGoalProgress) {
}

