package com.erudit.events;

import com.erudit.web.ValidationException;

import java.util.Locale;

public enum AnalyticsGranularity {
    DAY("toStartOfDay(occurred_at, 'UTC')", "dau"),
    WEEK("toStartOfWeek(occurred_at, 1, 'UTC')", "wau"),
    MONTH("toStartOfMonth(occurred_at, 'UTC')", "mau");

    private final String bucketExpression;
    private final String activeUserMetric;

    AnalyticsGranularity(String bucketExpression, String activeUserMetric) {
        this.bucketExpression = bucketExpression;
        this.activeUserMetric = activeUserMetric;
    }

    public String bucketExpression() { return bucketExpression; }
    public String activeUserMetric() { return activeUserMetric; }

    public static AnalyticsGranularity parse(String value) {
        try {
            return valueOf(value == null ? "DAY" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("granularity must be DAY, WEEK or MONTH");
        }
    }
}

