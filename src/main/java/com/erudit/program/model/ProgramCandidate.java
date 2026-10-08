package com.erudit.program.model;

import com.erudit.content.model.Difficulty;

import java.util.UUID;

public record ProgramCandidate(UUID contentId, UUID quizId, String category, Difficulty difficulty) {}
