package com.erudit.notificator.model;
import java.util.UUID;
public record Template(UUID id, String code, String title, String body, boolean enabled) {}
