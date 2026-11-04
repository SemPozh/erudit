package com.erudit.notificator.model;
import java.util.List;
import java.util.UUID;
public record Preference(UUID userId, boolean enabled, List<String> channels) {}
