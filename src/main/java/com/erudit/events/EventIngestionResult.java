package com.erudit.events;

import java.util.UUID;

public record EventIngestionResult(UUID eventId, boolean accepted) {
}

