package com.erudit.program.model;

import java.util.List;
import java.util.UUID;

public record ProgramModule(UUID id, String topic, String title, int position,
                            double progressPercent, List<ProgramLesson> lessons) {}
