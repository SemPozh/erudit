package com.erudit.events.model;

import java.time.Instant;

public record AnalyticsPeriod(Instant from, Instant to, AnalyticsGranularity granularity) {
}

