package com.erudit.events.model;

import java.util.List;

public record AnalyticsMetricSeries(String metric, List<AnalyticsMetricPoint> points) {
}

