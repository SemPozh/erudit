package com.erudit.events.controller;

import com.erudit.events.model.AnalyticsMetricSeries;
import com.erudit.events.service.AnalyticsService;

import com.erudit.openapi.api.AnalyticsApi;
import com.erudit.openapi.model.MetricPoint;
import com.erudit.openapi.model.MetricSeries;
import com.erudit.openapi.model.MetricSeriesResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
public class AnalyticsController implements AnalyticsApi {
    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<MetricSeriesResponse> getActiveUsers(@Nullable OffsetDateTime from,
                                                               @Nullable OffsetDateTime to,
                                                               String granularity) {
        return ResponseEntity.ok(response(service.activeUsers(instant(from), instant(to), granularity)));
    }

    @Override
    public ResponseEntity<MetricSeriesResponse> getSessionMetrics(@Nullable OffsetDateTime from,
                                                                  @Nullable OffsetDateTime to,
                                                                  String granularity) {
        return ResponseEntity.ok(response(service.sessions(instant(from), instant(to), granularity)));
    }

    @Override public ResponseEntity<MetricSeriesResponse> getEngagementMetrics(
            OffsetDateTime from, OffsetDateTime to, String granularity) {
        return ResponseEntity.ok(response(service.engagement(instant(from), instant(to), granularity)));
    }
    @Override public ResponseEntity<MetricSeriesResponse> getLearningMetrics(
            OffsetDateTime from, OffsetDateTime to, String granularity) {
        return ResponseEntity.ok(response(service.learning(instant(from), instant(to), granularity)));
    }
    @Override public ResponseEntity<MetricSeriesResponse> getFunnelMetrics(
            OffsetDateTime from, OffsetDateTime to, String granularity) {
        return ResponseEntity.ok(response(service.funnels(instant(from), instant(to), granularity)));
    }
    @Override public ResponseEntity<MetricSeriesResponse> getMonetizationMetrics(
            OffsetDateTime from, OffsetDateTime to, String granularity) {
        return ResponseEntity.ok(response(service.monetization(instant(from), instant(to), granularity)));
    }
    @Override public ResponseEntity<MetricSeriesResponse> getNotificationMetrics(
            OffsetDateTime from, OffsetDateTime to, String granularity) {
        return ResponseEntity.ok(response(service.notifications(instant(from), instant(to), granularity)));
    }

    private static MetricSeriesResponse response(AnalyticsMetricSeries value) {
        var points = value.points().stream().map(point ->
                new MetricPoint(point.time().atOffset(ZoneOffset.UTC), point.value())
                        .dimensions(point.dimensions())).toList();
        return new MetricSeriesResponse(new MetricSeries(value.metric(), points));
    }

    private static java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
