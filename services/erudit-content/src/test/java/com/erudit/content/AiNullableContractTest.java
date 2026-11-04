package com.erudit.content;

import com.erudit.content.model.ContentItem;
import com.erudit.content.service.LocalQuizCardGenerator;
import com.erudit.integration.InternalClients.AiClient;
import com.erudit.integration.InternalContracts.AiCard;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiNullableContractTest {
    @Test void missingAiFieldsUseLocalFactsInsteadOfFailingContentCreation() {
        var ai=mock(AiClient.class);
        var beans=new StaticListableBeanFactory(Map.of("ai",ai));
        var generator=new LocalQuizCardGenerator(beans.getBeanProvider(AiClient.class),CircuitBreakerRegistry.ofDefaults());
        var item=new ContentItem(UUID.randomUUID(),UUID.randomUUID(),"ARTICLE","Title","Description",
                "Local fact.",null,"BEGINNER",1,false,"PUBLISHED",List.of(),Instant.now());
        for(var malformed:List.of(new AiCard(null,"summary"),new AiCard("title",null))){
            when(ai.card(any())).thenReturn(malformed);
            assertThat(generator.generate(item)).singleElement().satisfies(card -> assertThat(card.fact()).isEqualTo("Local fact."));
        }
    }
}
