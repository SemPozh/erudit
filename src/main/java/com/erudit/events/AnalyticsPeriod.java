package com.erudit.events;

import java.time.Instant;

public record AnalyticsPeriod(Instant from, Instant to, AnalyticsGranularity granularity) {
}

