package com.erudit.content;
import static org.assertj.core.api.Assertions.*;
import com.erudit.content.model.ContentItem;
import com.erudit.content.service.LocalQuizCardGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class LocalQuizCardGeneratorTest {
    private ContentItem item(String body,String description) {
        return new ContentItem(UUID.randomUUID(),UUID.randomUUID(),"ARTICLE","Title",description,body,null,"BEGINNER",1,false,"PUBLISHED",List.of(),Instant.now());
    }
    @Test void extractsOnlySourceFactsAndBoundsTheNumberOfCards() {
        var item=item("One fact. Two facts! Three facts? Fourth. Fifth. Sixth.","Ignored description");
        var cards=new LocalQuizCardGenerator().generate(item);
        assertThat(cards).hasSize(5);assertThat(cards.getFirst().fact()).isEqualTo("One fact.");
        assertThat(cards).allSatisfy(c -> {assertThat(c.contentId()).isEqualTo(item.id());assertThat(item.body()).contains(c.fact());assertThat(c.answers()).contains(c.fact());});
    }
    @Test void usesDescriptionThenTitleWhenBodyIsEmpty() {
        var generator=new LocalQuizCardGenerator();
        assertThat(generator.generate(item(null,"Description fact.")).getFirst().fact()).isEqualTo("Description fact.");
        assertThat(generator.generate(item(" ",null)).getFirst().fact()).isEqualTo("Title");
    }

    @Test void normalizesTabsAndLineBreaksBeforeExtractingFacts() {
        String body="First"+(char)9+"fact."+(char)10+"Second"+(char)9+"fact.";
        var cards=new LocalQuizCardGenerator().generate(item(body,"Ignored"));
        assertThat(cards).extracting(c -> c.fact()).containsExactly("First fact.","Second fact.");
    }
}
