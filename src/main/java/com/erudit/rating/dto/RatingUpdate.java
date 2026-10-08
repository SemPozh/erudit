package com.erudit.rating.dto;

public record RatingUpdate(boolean awarded, long points, int erScore, String gradeCode) {
}
