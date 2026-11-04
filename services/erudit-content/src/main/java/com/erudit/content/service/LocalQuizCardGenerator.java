package com.erudit.content.service;
import com.erudit.content.model.ContentItem;
import com.erudit.content.model.QuizCard;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.erudit.integration.InternalClients.AiClient;
import com.erudit.integration.InternalContracts.AiRequest;
import com.erudit.integration.ResilientExecutor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
@Component
public class LocalQuizCardGenerator implements QuizCardGenerator {
    private final ObjectProvider<AiClient> ai;
    private final CircuitBreakerRegistry breakers;
    public LocalQuizCardGenerator(){this.ai=null;this.breakers=null;}
    @Autowired public LocalQuizCardGenerator(ObjectProvider<AiClient> ai,CircuitBreakerRegistry breakers){this.ai=ai;this.breakers=breakers;}
    @Override public List<QuizCard> generate(ContentItem item) {
        String source = item.body() == null || item.body().isBlank() ? item.description() : item.body();
        if (source == null || source.isBlank()) source = item.title();
        AiClient client=ai==null?null:ai.getIfAvailable();
        if(client!=null){String text=source;var generated=ResilientExecutor.execute(breakers.circuitBreaker("ai"),()->client.card(new AiRequest(item.id().toString(),text)),()->null);if(generated!=null&&!generated.title().isBlank()&&!generated.summary().isBlank())return List.of(new QuizCard(UUID.randomUUID(),item.id(),0,generated.summary(),generated.title(),List.of(generated.summary())));}
        List<String> facts = Arrays.stream(source.replaceAll("\\s+"," ").trim().split("(?<=[.!?])\\s+"))
                .filter(s -> !s.isBlank()).limit(5).toList();
        return IntStream.range(0,facts.size()).mapToObj(i -> new QuizCard(UUID.randomUUID(),item.id(),i,
                facts.get(i),"Which fact " + (i+1) + " is explained in " + item.title() + "?",facts)).toList();
    }
}
