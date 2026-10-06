package com.erudit.user.service;

import java.util.Set;
import java.util.UUID;

public record UserPreferenceSnapshot(String language, String timeZone, Set<UUID> favoriteCategories,
                                     int dailyGoalMinutes, boolean visibleInSearch,
                                     boolean visibleInRating, boolean analyticsConsent) {
}
