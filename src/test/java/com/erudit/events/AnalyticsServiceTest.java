package com.erudit.events;

import com.erudit.web.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {
    private final AnalyticsMetricsRepository repository = mock(AnalyticsMetricsRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AnalyticsMetricsRepository> provider = mock(ObjectProvider.class);
    private final Instant now = Instant.parse("2026-10-06T12:00:00Z");
    private final AnalyticsService service = new AnalyticsService(provider, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void mapsGranularityToActiveUserMetricAndUsesDefaultPeriod() {
        when(provider.getIfAvailable()).thenReturn(repository);
        AnalyticsPeriod expected = new AnalyticsPeriod(now.minusSeconds(30L * 24 * 3600), now,
                AnalyticsGranularity.WEEK);
        when(repository.activeUsers(expected)).thenReturn(List.of(new AnalyticsMetricPoint(now, 12)));

        AnalyticsMetricSeries result = service.activeUsers(null, null, "WEEK");

        assertThat(result.metric()).isEqualTo("wau");
        assertThat(result.points()).containsExactly(new AnalyticsMetricPoint(now, 12));
    }

    @Test
    void validatesPeriodAndGranularity() {
        assertThatThrownBy(() -> service.sessions(now, now, "DAY"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("earlier");
        assertThatThrownBy(() -> service.sessions(now.minusSeconds(1), now, "HOUR"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("granularity");
    }

    @Test
    void exposesEngagementAndLearningSeries() {
        when(provider.getIfAvailable()).thenReturn(repository);
        AnalyticsPeriod period = new AnalyticsPeriod(now.minusSeconds(3600), now, AnalyticsGranularity.DAY);
        when(repository.engagement(period)).thenReturn(List.of(new AnalyticsMetricPoint(
                now.minusSeconds(1800), 4, java.util.Map.of("completions", 2.0))));
        when(repository.learning(period)).thenReturn(List.of(new AnalyticsMetricPoint(
                now.minusSeconds(1800), 3, java.util.Map.of("correctRate", 75.0))));

        assertThat(service.engagement(period.from(), period.to(), "DAY").metric()).isEqualTo("engagement");
        assertThat(service.learning(period.from(), period.to(), "DAY").points().getFirst().dimensions())
                .containsEntry("correctRate", 75.0);
    }

    @Test
    void exposesFunnelMonetizationAndNotificationSeries() {
        when(provider.getIfAvailable()).thenReturn(repository);
        AnalyticsPeriod period = new AnalyticsPeriod(now.minusSeconds(3600), now, AnalyticsGranularity.DAY);
        AnalyticsMetricPoint point = new AnalyticsMetricPoint(now.minusSeconds(1800), 2,
                java.util.Map.of("conversionRate", 50.0));
        when(repository.funnels(period)).thenReturn(List.of(point));
        when(repository.monetization(period)).thenReturn(List.of(point));
        when(repository.notifications(period)).thenReturn(List.of(point));

        assertThat(service.funnels(period.from(), period.to(), "DAY").metric()).isEqualTo("funnels");
        assertThat(service.monetization(period.from(), period.to(), "DAY").metric()).isEqualTo("monetization");
        assertThat(service.notifications(period.from(), period.to(), "DAY").metric()).isEqualTo("notifications");
    }
}

