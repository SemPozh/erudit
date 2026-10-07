package com.erudit.events;

import java.time.Instant;

public record AnalyticsMetricPoint(Instant time, double value) {
}

