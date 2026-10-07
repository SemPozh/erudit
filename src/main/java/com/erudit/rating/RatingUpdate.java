package com.erudit.rating;

public record RatingUpdate(boolean awarded, long points, int erScore, String gradeCode) {
}
