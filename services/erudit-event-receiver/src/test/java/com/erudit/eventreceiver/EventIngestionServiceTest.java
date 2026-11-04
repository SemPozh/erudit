package com.erudit.eventreceiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erudit.eventreceiver.model.EventCommand;
import com.erudit.eventreceiver.repository.EventReceiptRepository;
import com.erudit.eventreceiver.service.ClickHouseEventStore;
import com.erudit.eventreceiver.service.EventIngestionService;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EventIngestionServiceTest {
    EventReceiptRepository receipts = mock(EventReceiptRepository.class);
    ClickHouseEventStore store = mock(ClickHouseEventStore.class);
    EventIngestionService service = new EventIngestionService(receipts, store);

    @BeforeEach void setUp() throws Exception {
        set("retries", 0); set("buffer", 8);
        when(store.initialize()).thenReturn(Mono.empty()); when(store.write(any())).thenReturn(Mono.empty());
        when(receipts.delivered(any())).thenReturn(Mono.empty()); when(receipts.failed(any(),anyString())).thenReturn(Mono.empty());
        service.start();
    }

    @Test void acceptsOnceAndMarksDuplicates() {
        EventCommand event = event(UUID.randomUUID());
        when(receipts.insertIfAbsent(event)).thenReturn(Mono.just(true), Mono.just(false));
        StepVerifier.create(service.ingest(event)).assertNext(v -> assertThat(v.duplicate()).isFalse()).verifyComplete();
        StepVerifier.create(service.ingest(event)).assertNext(v -> assertThat(v.duplicate()).isTrue()).verifyComplete();
    }

    @Test void validatesBatchAndAppliesBackpressure() {
        var first=event(UUID.randomUUID()); var second=event(UUID.randomUUID());
        when(receipts.insertIfAbsent(any())).thenReturn(Mono.just(true));
        StepVerifier.create(service.ingest(List.of(first,second)),1).expectNextCount(1).thenRequest(1).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.ingest(java.util.Collections.nCopies(101,first))).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.ingest(new EventCommand(null,null,"",OffsetDateTime.now(),Map.of())))
                .expectError(IllegalArgumentException.class).verify();
    }

    private EventCommand event(UUID id){return new EventCommand(id,UUID.randomUUID(),"CONTENT_VIEW",OffsetDateTime.now(),Map.of("contentId","demo"));}
    private void set(String name,Object value)throws Exception{Field f=EventIngestionService.class.getDeclaredField(name);f.setAccessible(true);f.set(service,value);}
}
