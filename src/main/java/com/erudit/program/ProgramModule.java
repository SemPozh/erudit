package com.erudit.program;

import java.util.List;
import java.util.UUID;

public record ProgramModule(UUID id, String topic, String title, int position,
                            double progressPercent, List<ProgramLesson> lessons) {}
