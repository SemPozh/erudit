package com.erudit.program;

import com.erudit.assessment.AssessmentService;
import com.erudit.assessment.AssessmentSubmission;
import com.erudit.assessment.TopicAssessmentScore;
import com.erudit.content.Difficulty;
import com.erudit.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.erudit.user.service.UserPreferenceProvider;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

@Service
public class ProgramService {
    private static final Map<String, String> TOPIC_TITLES = Map.of(
            "SCIENCE", "Наука", "HISTORY", "История", "TECHNOLOGY", "Технологии", "CULTURE", "Культура");
    private final ProgramRepository repository;
    private final AssessmentService assessmentService;
    private final ProgramPreferenceService preferenceService;
    private final ObjectProvider<UserPreferenceProvider> userPreferences;
    private final Clock clock;

    public ProgramService(ProgramRepository repository, AssessmentService assessmentService,
                          ProgramPreferenceService preferenceService,
                          ObjectProvider<UserPreferenceProvider> userPreferences, Clock clock) {
        this.repository = repository;
        this.assessmentService = assessmentService;
        this.preferenceService = preferenceService;
        this.userPreferences = userPreferences;
        this.clock = clock;
    }

    @Transactional
    public LearningProgram generate(String userId) {
        AssessmentSubmission assessment = assessmentService.latestResult(userId);
        ProgramPreferences preferences = preferenceService.aggregate(userId, assessment.topicScores());
        Map<String, List<ProgramCandidate>> candidates = repository.findCandidates().stream()
                .collect(Collectors.groupingBy(candidate -> normalizeTopic(candidate.category())));
        List<TopicAssessmentScore> orderedTopics = assessment.topicScores().stream()
                .sorted(Comparator.<TopicAssessmentScore>comparingInt(topic -> -preferences.weight(topic.topic()))
                        .thenComparingInt(TopicAssessmentScore::erScore)
                        .thenComparing(TopicAssessmentScore::topic)).toList();
        List<ProgramModule> modules = new ArrayList<>();
        for (TopicAssessmentScore topic : orderedTopics) {
            ProgramModule module = module(topic, candidates.getOrDefault(topic.topic(), List.of()), modules.size());
            if (!module.lessons().isEmpty()) modules.add(module);
        }
        if (modules.isEmpty()) throw new NotFoundException("No published content with quizzes is available");

        LearningProgram program = new LearningProgram(UUID.randomUUID(), userId, Instant.now(), 0, modules);
        repository.replace(userId, program);
        return repository.findByUserId(userId).orElseThrow();
    }

    public LearningProgram get(String userId) {
        return repository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Learning program not found"));
    }

    public NextStep next(String userId) {
        LearningProgram program = get(userId);
        ProgramLesson lesson = program.modules().stream().flatMap(module -> module.lessons().stream())
                .filter(value -> value.status() != LessonStatus.COMPLETED).findFirst().orElse(null);
        ZoneId zone = ZoneId.of("UTC");
        int goal = 15;
        try {
            UUID id = UUID.fromString(userId);
            UserPreferenceProvider provider = userPreferences.getIfAvailable();
            if (provider != null) {
                var preferences = provider.preferencesFor(id);
                zone = ZoneId.of(preferences.timeZone());
                goal = preferences.dailyGoalMinutes();
            }
        } catch (IllegalArgumentException ignored) {
            // Legacy users use documented defaults.
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        double progress = Math.min(100.0, repository.completedMinutes(userId, dayStart, dayEnd) * 100.0 / goal);
        int streak = streak(repository.completionTimes(userId), today, zone);
        return new NextStep(lesson == null ? null : lesson.contentId(), lesson == null ? null : lesson.quizId(),
                lesson == null ? "DAILY_GOAL_COMPLETE" : "CONTINUE_PROGRAM", streak,
                Math.round(progress * 100.0) / 100.0);
    }

    static int streak(List<Instant> completions, LocalDate today, ZoneId zone) {
        Set<LocalDate> days = completions.stream().map(value -> value.atZone(zone).toLocalDate())
                .collect(Collectors.toSet());
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int count = 0;
        while (days.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    private ProgramModule module(TopicAssessmentScore topic, List<ProgramCandidate> candidates, int position) {
        Difficulty target = difficulty(topic.erScore());
        return candidates.stream()
                .min(Comparator.comparingInt(candidate -> distance(candidate.difficulty(), target)))
                .map(candidate -> new ProgramModule(UUID.randomUUID(), topic.topic(),
                        TOPIC_TITLES.getOrDefault(topic.topic(), topic.topic()), position, 0,
                        List.of(new ProgramLesson(UUID.randomUUID(), candidate.contentId(), candidate.quizId(),
                                0, LessonStatus.NOT_STARTED))))
                .orElseGet(() -> new ProgramModule(UUID.randomUUID(), topic.topic(),
                        TOPIC_TITLES.getOrDefault(topic.topic(), topic.topic()), position, 0, List.of()));
    }

    private Difficulty difficulty(int score) {
        if (score < 700) return Difficulty.BEGINNER;
        if (score < 1400) return Difficulty.INTERMEDIATE;
        return Difficulty.ADVANCED;
    }

    private int distance(Difficulty actual, Difficulty target) {
        return Math.abs(actual.ordinal() - target.ordinal());
    }

    private String normalizeTopic(String category) {
        return ProgramPreferenceService.normalize(category);
    }
}
