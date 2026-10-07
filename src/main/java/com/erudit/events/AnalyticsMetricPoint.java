package com.erudit.events;

import java.time.Instant;
import java.util.Map;

public record AnalyticsMetricPoint(Instant time, double value, Map<String, Double> dimensions) {
    public AnalyticsMetricPoint(Instant time, double value) {
        this(time, value, Map.of());
    }

    public AnalyticsMetricPoint {
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }
}

