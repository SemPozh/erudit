package com.erudit.program.service;

import com.erudit.program.model.ProgramPreferences;
import com.erudit.program.repository.ProgramPreferenceRepository;

import com.erudit.assessment.model.TopicAssessmentScore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ProgramPreferenceService {
    private final ProgramPreferenceRepository repository;

    public ProgramPreferenceService(ProgramPreferenceRepository repository) {
        this.repository = repository;
    }

    public ProgramPreferences aggregate(String userId, List<TopicAssessmentScore> assessment) {
        Map<String, Integer> weights = new HashMap<>();
        // Explicit profile choices have the highest priority.
        try {
            repository.favoriteTopics(UUID.fromString(userId))
                    .forEach((topic, weight) -> weights.merge(normalize(topic), weight, Integer::sum));
        } catch (IllegalArgumentException ignored) {
            // Legacy/non-UUID users still receive assessment and behavioral personalization.
        }
        // Completing onboarding/assessment is an explicit signal; weak topics get a little more weight.
        assessment.forEach(score -> weights.merge(normalize(score.topic()),
                50 + Math.max(0, 2000 - score.erScore()) / 100, Integer::sum));
        // Repeated real interactions reinforce, but cannot override explicit choices on their own.
        repository.behavioralTopics(userId)
                .forEach((topic, weight) -> weights.merge(normalize(topic), weight, Integer::sum));
        return new ProgramPreferences(Map.copyOf(weights));
    }

    static String normalize(String category) {
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "НАУКА" -> "SCIENCE";
            case "ИСТОРИЯ" -> "HISTORY";
            case "ТЕХНОЛОГИИ" -> "TECHNOLOGY";
            case "КУЛЬТУРА" -> "CULTURE";
            default -> normalized;
        };
    }
}

