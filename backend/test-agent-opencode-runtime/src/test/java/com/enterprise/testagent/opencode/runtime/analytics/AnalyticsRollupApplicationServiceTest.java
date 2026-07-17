package com.enterprise.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsRollupApplicationServiceTest {

    private AnalyticsRepository repository;
    private AnalyticsRollupApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(AnalyticsRepository.class);
        service = new AnalyticsRollupApplicationService(repository);
        when(repository.loadRawActivityFacts(any(), any())).thenReturn(List.of());
        when(repository.loadRunDurationSamples(any(), any())).thenReturn(List.of());
        when(repository.loadHourly(any(), any())).thenReturn(List.of());
    }

    @Test
    void returnsNotExecutedWhenCompatibilityLockIsHeldByAnotherOwner() {
        when(repository.tryAcquireLock(anyString(), anyString(), any(), any())).thenReturn(false);

        AnalyticsRollupApplicationService.Result result = service.rollupRecent("trace-lock", () -> false);

        assertThat(result.executed()).isFalse();
        assertThat(result.stopped()).isFalse();
        assertThat(result.toMap())
                .containsEntry("executed", false)
                .containsEntry("stopped", false);
        verify(repository, never()).loadRawActivityFacts(any(), any());
        verify(repository, never()).releaseLock(anyString(), anyString());
    }

    @Test
    void stopsBetweenHourlyAndDailyStagesAndReleasesCompatibilityLock() {
        when(repository.tryAcquireLock(anyString(), anyString(), any(), any())).thenReturn(true);
        AtomicInteger stopChecks = new AtomicInteger();

        AnalyticsRollupApplicationService.Result result = service.rollupRecent(
                "trace-stop",
                () -> stopChecks.incrementAndGet() >= 3);

        assertThat(result.executed()).isTrue();
        assertThat(result.stopped()).isTrue();
        verify(repository).loadRawActivityFacts(any(), any());
        verify(repository, never()).loadHourly(any(), any());
        verify(repository, never()).updateWatermark(
                anyString(), any(), any(), anyString(), anyString(), any());
        verify(repository).releaseLock(eq(AnalyticsRollupApplicationService.JOB_NAME), anyString());
    }

    @Test
    void returnsCompletedWindowAndUpdatesFreshWatermark() {
        when(repository.tryAcquireLock(anyString(), anyString(), any(), any())).thenReturn(true);

        AnalyticsRollupApplicationService.Result result = service.rollupRecent("trace-success", () -> false);

        assertThat(result.executed()).isTrue();
        assertThat(result.stopped()).isFalse();
        assertThat(result.hourlyWindowStart()).isBefore(result.hourlyWindowEnd());
        assertThat(result.dailyStart()).isBeforeOrEqualTo(result.dailyEnd());
        assertThat(result.toMap())
                .containsEntry("executed", true)
                .containsEntry("stopped", false)
                .containsKeys("hourlyWindowStart", "hourlyWindowEnd", "dailyStart", "dailyEnd");
        verify(repository).updateWatermark(
                eq(AnalyticsRollupApplicationService.JOB_NAME),
                eq(result.hourlyWindowEnd()),
                eq(AnalyticsModels.FreshnessStatus.FRESH),
                eq("统计已更新"),
                eq("trace-success"),
                any());
        verify(repository).releaseLock(eq(AnalyticsRollupApplicationService.JOB_NAME), anyString());
    }

    @Test
    void recordsFailedWatermarkAndReleasesLockWhenRollupFails() {
        when(repository.tryAcquireLock(anyString(), anyString(), any(), any())).thenReturn(true);
        when(repository.loadRawActivityFacts(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.rollupRecent("trace-failed", () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(repository).updateWatermark(
                eq(AnalyticsRollupApplicationService.JOB_NAME),
                any(),
                eq(AnalyticsModels.FreshnessStatus.FAILED),
                eq("boom"),
                eq("trace-failed"),
                any());
        verify(repository).releaseLock(eq(AnalyticsRollupApplicationService.JOB_NAME), anyString());
    }
}
