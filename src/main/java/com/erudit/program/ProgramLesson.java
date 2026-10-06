package com.erudit.program;

import java.util.UUID;

public record ProgramLesson(UUID id, UUID contentId, UUID quizId, int position, LessonStatus status) {}
