package com.erudit.rating.dto;

public record RatingUpdateResponse(boolean awarded, long points, int erScore, String gradeCode) {
}
