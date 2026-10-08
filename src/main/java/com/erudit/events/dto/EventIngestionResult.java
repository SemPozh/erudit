package com.erudit.events.dto;

import java.util.UUID;

public record EventIngestionResult(UUID eventId, boolean accepted) {
}

