package com.erudit.events.dto;

import java.util.UUID;

public record EventIngestionResponse(UUID eventId, boolean accepted) {
}
