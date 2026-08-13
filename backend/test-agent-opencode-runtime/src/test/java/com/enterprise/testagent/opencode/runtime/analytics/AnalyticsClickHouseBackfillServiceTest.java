package com.enterprise.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.analytics.AnalyticsBackfillSource;
import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import com.enterprise.testagent.domain.analytics.AnalyticsEventSink;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsClickHouseBackfillServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-01-03T00:00:00Z");

    @Test
    void verifiesEveryEventBeforeCleaningLegacyRollups() {
        AnalyticsBackfillSource source = mock(AnalyticsBackfillSource.class);
        AnalyticsEventSink sink = mock(AnalyticsEventSink.class);
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        when(repository.tryAcquireLock(any(), any(), any(), any())).thenReturn(true);
        when(source.loadDimensionEvents()).thenReturn(List.of(event("backfill-v1:user-dimension:1")));
        when(source.loadEvents(any(), any())).thenReturn(List.of(event("backfill-v1:run:1")));
        when(sink.countEventsByPrefix("backfill-v1:")).thenReturn(3L);
        when(sink.countActivityFactsByPrefix("backfill-v1:")).thenReturn(2L);
        when(sink.countUserDimensionFactsByPrefix("backfill-v1:")).thenReturn(1L);

        var result = service(source, sink, repository).backfill(START, END, true);

        assertThat(result.verified()).isTrue();
        assertThat(result.sourceEvents()).isEqualTo(3);
        verify(source).markVerified("analytics-v1", 3, 3, START, END, END);
        verify(source).cleanupLegacyRollups();
        verify(repository).updateWatermark(
                AnalyticsRollupApplicationService.JOB_NAME,
                END,
                AnalyticsModels.FreshnessStatus.FRESH,
                "ClickHouse 历史数据已回填",
                "analytics-clickhouse-backfill",
                END);
        verify(repository).releaseLock(any(), any());
    }

    @Test
    void refusesCleanupWhenClickHouseCountsDoNotMatch() {
        AnalyticsBackfillSource source = mock(AnalyticsBackfillSource.class);
        AnalyticsEventSink sink = mock(AnalyticsEventSink.class);
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        when(repository.tryAcquireLock(any(), any(), any(), any())).thenReturn(true);
        when(source.loadDimensionEvents()).thenReturn(List.of());
        when(source.loadEvents(any(), any())).thenReturn(List.of(event("backfill-v1:run:1")));
        when(sink.countEventsByPrefix("backfill-v1:")).thenReturn(1L);
        when(sink.countActivityFactsByPrefix("backfill-v1:")).thenReturn(0L);
        when(sink.countUserDimensionFactsByPrefix("backfill-v1:")).thenReturn(0L);

        assertThatThrownBy(() -> service(source, sink, repository).backfill(START, END, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("校验失败");
        verify(source, never()).cleanupLegacyRollups();
        verify(source, never()).markVerified(any(), any(Long.class), any(Long.class), any(), any(), any());
    }

    @Test
    void skipsCompletedCutoverOnRestart() {
        AnalyticsBackfillSource source = mock(AnalyticsBackfillSource.class);
        AnalyticsEventSink sink = mock(AnalyticsEventSink.class);
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        when(repository.tryAcquireLock(any(), any(), any(), any())).thenReturn(true);
        when(source.alreadyVerified("analytics-v1")).thenReturn(true);

        var result = service(source, sink, repository).backfill(START, END, false);

        assertThat(result.skipped()).isTrue();
        verify(source, never()).loadEvents(any(), any());
        verify(source, never()).cleanupLegacyRollups();
    }

    @Test
    void allowsCleanupAfterPreviouslyVerifiedDryCutover() {
        AnalyticsBackfillSource source = mock(AnalyticsBackfillSource.class);
        AnalyticsEventSink sink = mock(AnalyticsEventSink.class);
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        when(repository.tryAcquireLock(any(), any(), any(), any())).thenReturn(true);
        when(source.alreadyVerified("analytics-v1")).thenReturn(true);

        var result = service(source, sink, repository).backfill(START, END, true);

        assertThat(result.skipped()).isTrue();
        assertThat(result.cleaned()).isTrue();
        verify(source).cleanupLegacyRollups();
        verify(source, never()).loadEvents(any(), any());
    }

    private AnalyticsClickHouseBackfillService service(
            AnalyticsBackfillSource source,
            AnalyticsEventSink sink,
            AnalyticsRepository repository) {
        return new AnalyticsClickHouseBackfillService(
                source, sink, repository, Clock.fixed(END, ZoneOffset.UTC));
    }

    private AnalyticsEventOutboxRepository.Event event(String id) {
        return new AnalyticsEventOutboxRepository.Event(0, id, 1, "RUN", "1", START, "{}");
    }
}
