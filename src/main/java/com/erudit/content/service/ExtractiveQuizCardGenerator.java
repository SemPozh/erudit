package com.erudit.content.service;

import com.erudit.content.model.Content;
import com.erudit.content.model.QuizCard;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class ExtractiveQuizCardGenerator implements QuizCardGenerator {
    private static final int MAX_CARDS = 5;

    @Override
    public List<QuizCard> generate(Content content) {
        String source = firstNonBlank(content.body(), content.description(), content.title());
        List<String> extracted = Arrays.stream(source.replaceAll("\\s+", " ").trim()
                        .split("(?<=[.!?])\\s+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .limit(MAX_CARDS)
                .toList();
        List<String> facts = extracted.isEmpty() ? List.of(content.title()) : extracted;
        List<String> answers = facts;
        return java.util.stream.IntStream.range(0, facts.size())
                .mapToObj(index -> new QuizCard(UUID.randomUUID(), content.id(), index,
                        facts.get(index), question(content.title(), index, facts.size()),
                        facts.get(index), answers))
                .toList();
    }

    private static String question(String title, int index, int total) {
        return total == 1
                ? "Какой ключевой факт раскрывает материал «" + title + "»?"
                : "Какой факт №" + (index + 1) + " раскрывает материал «" + title + "»?";
    }

    private static String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("");
    }
}
