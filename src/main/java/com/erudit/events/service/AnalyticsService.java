package com.erudit.events.service;

import com.erudit.events.model.AnalyticsGranularity;
import com.erudit.events.model.AnalyticsMetricSeries;
import com.erudit.events.model.AnalyticsPeriod;
import com.erudit.events.repository.AnalyticsMetricsRepository;

import com.erudit.web.exception.ServiceUnavailableException;
import com.erudit.web.exception.ValidationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class AnalyticsService {
    private static final Duration DEFAULT_PERIOD = Duration.ofDays(30);
    private static final Duration MAX_PERIOD = Duration.ofDays(730);
    private final ObjectProvider<AnalyticsMetricsRepository> repositoryProvider;
    private final Clock clock;

    public AnalyticsService(ObjectProvider<AnalyticsMetricsRepository> repositoryProvider, Clock clock) {
        this.repositoryProvider = repositoryProvider;
        this.clock = clock;
    }

    public AnalyticsMetricSeries activeUsers(Instant from, Instant to, String granularity) {
        AnalyticsPeriod period = period(from, to, granularity);
        return new AnalyticsMetricSeries(period.granularity().activeUserMetric(), repository().activeUsers(period));
    }

    public AnalyticsMetricSeries sessions(Instant from, Instant to, String granularity) {
        AnalyticsPeriod period = period(from, to, granularity);
        return new AnalyticsMetricSeries("sessions", repository().sessions(period));
    }

    public AnalyticsMetricSeries engagement(Instant from, Instant to, String granularity) {
        AnalyticsPeriod period = period(from, to, granularity);
        return new AnalyticsMetricSeries("engagement", repository().engagement(period));
    }

    public AnalyticsMetricSeries learning(Instant from, Instant to, String granularity) {
        AnalyticsPeriod period = period(from, to, granularity);
        return new AnalyticsMetricSeries("learning", repository().learning(period));
    }

    public AnalyticsMetricSeries funnels(Instant from, Instant to, String granularity) {
        AnalyticsPeriod period = period(from, to, granularity);
        return new AnalyticsMetricSeries("funnels", repository().funnels(period));
    }

    public AnalyticsMetricSeries monetization(Instant from, Instant to, String granularity) {
        AnalyticsPeriod period = period(from, to, granularity);
        return new AnalyticsMetricSeries("monetization", repository().monetization(period));
    }

    public AnalyticsMetricSeries notifications(Instant from, Instant to, String granularity) {
        AnalyticsPeriod period = period(from, to, granularity);
        return new AnalyticsMetricSeries("notifications", repository().notifications(period));
    }

    AnalyticsPeriod period(Instant requestedFrom, Instant requestedTo, String requestedGranularity) {
        Instant to = requestedTo == null ? clock.instant() : requestedTo;
        Instant from = requestedFrom == null ? to.minus(DEFAULT_PERIOD) : requestedFrom;
        if (!from.isBefore(to)) throw new ValidationException("from must be earlier than to");
        if (Duration.between(from, to).compareTo(MAX_PERIOD) > 0) {
            throw new ValidationException("analytics period cannot exceed 730 days");
        }
        return new AnalyticsPeriod(from, to, AnalyticsGranularity.parse(requestedGranularity));
    }

    private AnalyticsMetricsRepository repository() {
        AnalyticsMetricsRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) throw new ServiceUnavailableException("Analytics storage is unavailable");
        return repository;
    }
}

