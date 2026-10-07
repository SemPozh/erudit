package com.erudit.events;

import java.util.List;

public interface AnalyticsMetricsRepository {
    List<AnalyticsMetricPoint> activeUsers(AnalyticsPeriod period);
    List<AnalyticsMetricPoint> sessions(AnalyticsPeriod period);
    List<AnalyticsMetricPoint> engagement(AnalyticsPeriod period);
    List<AnalyticsMetricPoint> learning(AnalyticsPeriod period);
}

