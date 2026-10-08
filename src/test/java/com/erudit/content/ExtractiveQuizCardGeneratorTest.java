package com.erudit.content;

import com.erudit.content.model.Content;
import com.erudit.content.model.ContentStatus;
import com.erudit.content.model.ContentType;
import com.erudit.content.model.Difficulty;
import com.erudit.content.model.QuizCard;
import com.erudit.content.service.ExtractiveQuizCardGenerator;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractiveQuizCardGeneratorTest {
    private final ExtractiveQuizCardGenerator generator = new ExtractiveQuizCardGenerator();

    @Test
    void extractsFactsQuestionsCorrectAnswersAndOptions() {
        Content content = new Content(UUID.randomUUID(), UUID.randomUUID(), ContentType.ARTICLE,
                "Планеты", "", "Земля вращается вокруг Солнца. Марс называют красной планетой!",
                null, Difficulty.BEGINNER, 3, "author", ContentStatus.PUBLISHED,
                Instant.now(), List.of(), false);

        List<QuizCard> cards = generator.generate(content);

        assertThat(cards).hasSize(2);
        assertThat(cards).extracting(QuizCard::fact).containsExactly(
                "Земля вращается вокруг Солнца.", "Марс называют красной планетой!");
        assertThat(cards.get(0).question()).contains("факт №1", "Планеты");
        assertThat(cards.get(0).correctAnswer()).isEqualTo(cards.get(0).fact());
        assertThat(cards.get(0).answers()).containsExactlyElementsOf(cards.stream().map(QuizCard::fact).toList());
    }

    @Test
    void fallsBackToTitleWhenFormatHasNoText() {
        Content content = new Content(UUID.randomUUID(), UUID.randomUUID(), ContentType.VIDEO,
                "Короткое видео", "", "", "media-id", Difficulty.BEGINNER, 1,
                "author", ContentStatus.DRAFT, Instant.now(), List.of(), false);

        assertThat(generator.generate(content)).singleElement()
                .satisfies(card -> assertThat(card.correctAnswer()).isEqualTo("Короткое видео"));
    }
}
