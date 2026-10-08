package com.erudit.program.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LearningProgram(UUID id, String userId, Instant createdAt,
                              double progressPercent, List<ProgramModule> modules) {}
