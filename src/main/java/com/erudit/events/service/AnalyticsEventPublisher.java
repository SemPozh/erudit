package com.erudit.events.service;

import com.erudit.events.dto.EventPublication;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AnalyticsEventPublisher {
    CompletableFuture<UUID> publish(EventPublication publication);
}
