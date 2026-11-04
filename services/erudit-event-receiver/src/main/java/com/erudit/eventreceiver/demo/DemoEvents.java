package com.erudit.eventreceiver.demo;

import com.erudit.eventreceiver.model.EventCommand;
import com.erudit.eventreceiver.service.EventIngestionService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("demo")
public class DemoEvents implements ApplicationRunner {
    private final EventIngestionService events;
    public DemoEvents(EventIngestionService events) { this.events = events; }
    @Override public void run(ApplicationArguments arguments) {
        String[] types = {"SESSION_STARTED", "CONTENT_COMPLETED", "QUIZ_COMPLETED", "PAYMENT_MOCK", "NOTIFICATION_MOCK"};
        for (int i = 0; i < types.length; i++) {
            var event = new EventCommand(UUID.fromString("e0000000-0000-0000-0000-00000000000" + (i + 1)),
                    UUID.fromString("00000000-0000-0000-0000-000000000001"), types[i],
                    OffsetDateTime.parse("2026-01-01T12:00:00Z"), Map.of("source", "demo-94"));
            // Startup thread only; actual persistence and ClickHouse work keep their reactive schedulers.
            events.ingest(event).block(java.time.Duration.ofSeconds(15));
        }
    }
}
