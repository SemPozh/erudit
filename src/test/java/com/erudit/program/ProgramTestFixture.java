package com.erudit.program;

import com.erudit.content.model.Category;
import com.erudit.content.model.Content;
import com.erudit.content.repository.ContentRepository;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.model.ContentType;
import com.erudit.content.model.Difficulty;
import com.erudit.quiz.model.Quiz;
import com.erudit.quiz.model.QuizAnswerOption;
import com.erudit.quiz.model.QuizQuestion;
import com.erudit.quiz.repository.QuizRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ProgramTestFixture {
    private ProgramTestFixture() {}

    static LessonIds lesson(ContentRepository contentRepository, QuizRepository quizRepository,
                            String topic, Difficulty difficulty) {
        UUID categoryId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        contentRepository.saveCategory(new Category(categoryId, topic));
        Content content = new Content(contentId, categoryId, ContentType.ARTICLE, topic + " lesson", "", "",
                null, difficulty, 5, "author", ContentStatus.PUBLISHED, Instant.now(), List.of(), false);
        contentRepository.save(content);
        Quiz quiz = new Quiz(UUID.randomUUID(), contentId, topic + " quiz", Instant.now(), List.of(
                new QuizQuestion(UUID.randomUUID(), 0, "Question", null, List.of(
                        new QuizAnswerOption(UUID.randomUUID(), 0, "Correct", true),
                        new QuizAnswerOption(UUID.randomUUID(), 1, "Wrong", false)))));
        quizRepository.save(quiz);
        return new LessonIds(contentId, quiz.id());
    }

    record LessonIds(UUID contentId, UUID quizId) {}
}
