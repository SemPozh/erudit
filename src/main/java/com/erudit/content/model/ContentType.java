package com.erudit.content.model;

import java.util.Locale;

public enum ContentType {
    ARTICLE, FACT_CARD, AUDIO, VIDEO, COMBINED, QUIZ_BLOCK;

    public static ContentType fromValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
