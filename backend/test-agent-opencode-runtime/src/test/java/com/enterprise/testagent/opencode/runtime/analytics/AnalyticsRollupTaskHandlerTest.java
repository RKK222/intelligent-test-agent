package com.enterprise.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import com.enterprise.testagent.scheduler.ScheduledTaskStopRequestedException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalyticsRollupTaskHandlerTest {

    private static final String TRACE_ID = "trace_analytics_rollup1234567890";
    private static final Instant FIRE_AT = Instant.parse("2026-07-15T02:00:00Z");

    @Test
    void exposesStableTaskMetadata() {
        AnalyticsRollupTaskHandler handler = new AnalyticsRollupTaskHandler(
                mock(AnalyticsRollupApplicationService.class));

        assertThat(handler.taskKey()).isEqualTo(new ScheduledTaskKey("opencode-runtime.analytics-rollup"));
        assertThat(handler.name()).isEqualTo("TestAgent 运营分析汇总");
        assertThat(handler.cronExpression()).isEqualTo("0 */5 * * * *");
        assertThat(handler.lockTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void delegatesTraceAndDynamicStopSignalAndReturnsSummary() {
        AnalyticsRollupApplicationService service = mock(AnalyticsRollupApplicationService.class);
        AnalyticsRollupApplicationService.Result serviceResult = completedResult();
        when(service.rollupRecent(eq(TRACE_ID), any())).thenReturn(serviceResult);
        AnalyticsRollupTaskHandler handler = new AnalyticsRollupTaskHandler(service);
        AtomicBoolean stopped = new AtomicBoolean(false);

        ScheduledTaskResult result = handler.run(context(stopped));

        assertThat(result.result())
                .containsEntry("executed", true)
                .containsEntry("stopped", false)
                .containsEntry("hourlyWindowEnd", "2026-07-15T03:00:00Z");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<BooleanSupplier> stopSupplier = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(service).rollupRecent(eq(TRACE_ID), stopSupplier.capture());
        stopped.set(true);
        assertThat(stopSupplier.getValue().getAsBoolean()).isTrue();
    }

    @Test
    void refusesToStartWhenStopWasAlreadyRequested() {
        AnalyticsRollupApplicationService service = mock(AnalyticsRollupApplicationService.class);
        AnalyticsRollupTaskHandler handler = new AnalyticsRollupTaskHandler(service);

        assertThatThrownBy(() -> handler.run(context(new AtomicBoolean(true))))
                .isInstanceOf(ScheduledTaskStopRequestedException.class);

        verify(service, never()).rollupRecent(any(), any());
    }

    @Test
    void convertsStopRequestedDuringServiceCallToFrameworkStopSignal() {
        AnalyticsRollupApplicationService service = mock(AnalyticsRollupApplicationService.class);
        AtomicBoolean stopped = new AtomicBoolean(false);
        when(service.rollupRecent(eq(TRACE_ID), any())).thenAnswer(invocation -> {
            stopped.set(true);
            return completedResult();
        });
        AnalyticsRollupTaskHandler handler = new AnalyticsRollupTaskHandler(service);

        assertThatThrownBy(() -> handler.run(context(stopped)))
                .isInstanceOf(ScheduledTaskStopRequestedException.class);
    }

    private static AnalyticsRollupApplicationService.Result completedResult() {
        return new AnalyticsRollupApplicationService.Result(
                true,
                false,
                Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-15T03:00:00Z"),
                LocalDate.parse("2026-07-08"),
                LocalDate.parse("2026-07-15"));
    }

    private static ScheduledTaskContext context(AtomicBoolean stopped) {
        return new ScheduledTaskContext(
                new ScheduledTaskRunId("str_analytics_rollup1234567890"),
                new ScheduledTaskKey("opencode-runtime.analytics-rollup"),
                null,
                ScheduledTaskTriggerType.CRON,
                null,
                FIRE_AT,
                TRACE_ID,
                Map.of(),
                stopped::get);
    }
}
