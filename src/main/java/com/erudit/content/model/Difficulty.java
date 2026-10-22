package com.erudit.content.model;

import java.util.Locale;

public enum Difficulty {
    BEGINNER, INTERMEDIATE, ADVANCED;

    public static Difficulty fromValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
