package com.erudit.content.service;
import com.erudit.content.model.ContentItem;
import com.erudit.content.model.QuizCard;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
@Component
public class LocalQuizCardGenerator implements QuizCardGenerator {
    @Override public List<QuizCard> generate(ContentItem item) {
        String source = item.body() == null || item.body().isBlank() ? item.description() : item.body();
        if (source == null || source.isBlank()) source = item.title();
        List<String> facts = Arrays.stream(source.replaceAll("\\s+"," ").trim().split("(?<=[.!?])\\s+"))
                .filter(s -> !s.isBlank()).limit(5).toList();
        return IntStream.range(0,facts.size()).mapToObj(i -> new QuizCard(UUID.randomUUID(),item.id(),i,
                facts.get(i),"Which fact " + (i+1) + " is explained in " + item.title() + "?",facts)).toList();
    }
}
