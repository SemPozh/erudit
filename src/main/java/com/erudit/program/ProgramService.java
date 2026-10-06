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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class ProgramService {
    private static final Map<String, String> TOPIC_TITLES = Map.of(
            "SCIENCE", "Наука", "HISTORY", "История", "TECHNOLOGY", "Технологии", "CULTURE", "Культура");
    private final ProgramRepository repository;
    private final AssessmentService assessmentService;

    public ProgramService(ProgramRepository repository, AssessmentService assessmentService) {
        this.repository = repository;
        this.assessmentService = assessmentService;
    }

    @Transactional
    public LearningProgram generate(String userId) {
        AssessmentSubmission assessment = assessmentService.latestResult(userId);
        Map<String, List<ProgramCandidate>> candidates = repository.findCandidates().stream()
                .collect(Collectors.groupingBy(candidate -> normalizeTopic(candidate.category())));
        List<TopicAssessmentScore> orderedTopics = assessment.topicScores().stream()
                .sorted(Comparator.comparingInt(TopicAssessmentScore::erScore)
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
