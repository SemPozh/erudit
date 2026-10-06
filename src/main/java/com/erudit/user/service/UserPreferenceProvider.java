package com.erudit.user.service;

import java.util.UUID;

public interface UserPreferenceProvider {
    UserPreferenceSnapshot preferencesFor(UUID userId);

    default boolean analyticsAllowed(UUID userId) {
        return preferencesFor(userId).analyticsConsent();
    }
}
