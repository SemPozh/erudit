package com.erudit.assessment;

import com.erudit.web.NotFoundException;
import com.erudit.web.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssessmentService {
    private final AssessmentRepository repository;
    private final ErScoreCalculator scoreCalculator;

    public AssessmentService(AssessmentRepository repository, ErScoreCalculator scoreCalculator) {
        this.repository = repository;
        this.scoreCalculator = scoreCalculator;
    }

    @Transactional
    public AssessmentSession start(String userId) {
        List<AssessmentQuestion> questions = repository.findActiveQuestions();
        Set<String> topics = questions.stream().map(AssessmentQuestion::topic).collect(Collectors.toSet());
        if (!topics.containsAll(Set.of("SCIENCE", "HISTORY", "TECHNOLOGY", "CULTURE"))) {
            throw new IllegalStateException("Assessment question bank does not cover every required topic");
        }
        UUID id = UUID.randomUUID();
        repository.createSession(id, userId, Instant.now(), questions);
        return new AssessmentSession(id, userId, questions);
    }

    @Transactional
    public AssessmentSubmission submit(String userId, UUID assessmentId, List<SubmittedAnswer> answers) {
        AssessmentRepository.SessionState session = repository.findSession(assessmentId)
                .orElseThrow(() -> new NotFoundException("Assessment not found"));
        if (!session.userId().equals(userId)) throw new NotFoundException("Assessment not found");
        if (!session.status().equals("STARTED")) throw new ValidationException("Assessment is already submitted");

        List<AssessmentQuestion> questions = repository.findSessionQuestions(assessmentId);
        Set<UUID> expected = questions.stream().map(AssessmentQuestion::id).collect(Collectors.toSet());
        Set<UUID> received = answers.stream().map(SubmittedAnswer::questionId).collect(Collectors.toSet());
        if (answers.size() != expected.size() || !received.equals(expected)) {
            throw new ValidationException("Exactly one answer is required for every assessment question");
        }

        Map<UUID, AssessmentOption> options = questions.stream().flatMap(question -> question.answers().stream())
                .collect(Collectors.toMap(AssessmentOption::id, Function.identity()));
        for (SubmittedAnswer answer : answers) {
            AssessmentOption option = options.get(answer.answerId());
            if (option == null || !option.questionId().equals(answer.questionId())) {
                throw new ValidationException("answerId does not belong to questionId");
            }
        }
        Map<UUID, AssessmentQuestion> questionsById = questions.stream()
                .collect(Collectors.toMap(AssessmentQuestion::id, Function.identity()));
        List<AnswerOutcome> outcomes = answers.stream().map(answer -> new AnswerOutcome(
                questionsById.get(answer.questionId()).topic(), options.get(answer.answerId()).correct())).toList();
        int correct = (int) outcomes.stream().filter(AnswerOutcome::correct).count();
        AssessmentScore score = scoreCalculator.calculate(outcomes);
        AssessmentSubmission result = new AssessmentSubmission(UUID.randomUUID(), correct, questions.size(),
                score.erScore(), score.topics());
        repository.submit(assessmentId, userId, answers, options, Instant.now(), result);
        return result;
    }

    public AssessmentSubmission latestResult(String userId) {
        return repository.findLatestResult(userId)
                .orElseThrow(() -> new NotFoundException("Assessment result not found"));
    }
}
