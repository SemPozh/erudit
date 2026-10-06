package com.erudit.program;

import com.erudit.content.Difficulty;

import java.util.UUID;

public record ProgramCandidate(UUID contentId, UUID quizId, String category, Difficulty difficulty) {}
