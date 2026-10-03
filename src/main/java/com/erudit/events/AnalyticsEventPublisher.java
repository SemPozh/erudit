package com.erudit.events;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AnalyticsEventPublisher {
    CompletableFuture<UUID> publish(EventPublication publication);
}
