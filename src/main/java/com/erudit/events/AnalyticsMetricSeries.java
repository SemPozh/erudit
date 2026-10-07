package com.erudit.events;

import java.util.List;

public record AnalyticsMetricSeries(String metric, List<AnalyticsMetricPoint> points) {
}

