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
}

