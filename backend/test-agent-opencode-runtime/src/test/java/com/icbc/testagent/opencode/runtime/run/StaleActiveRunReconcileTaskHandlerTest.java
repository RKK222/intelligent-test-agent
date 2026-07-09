package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.scheduler.ScheduledTaskContext;
import com.icbc.testagent.scheduler.ScheduledTaskResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 stale active Run 收敛任务的调度契约，不改变 scheduler 框架自身启动扫描行为。
 */
class StaleActiveRunReconcileTaskHandlerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-07-09T10:00:00Z");
    private static final String TRACE_ID = "trace_stale_task1234567890";

    @Test
    void exposesFiveMinuteCronAndFiveMinuteLock() {
        StaleActiveRunReconcileTaskHandler handler = new StaleActiveRunReconcileTaskHandler(
                mock(StaleActiveRunReconcileService.class),
                Clock.fixed(STARTED_AT, ZoneOffset.UTC));

        assertThat(handler.taskKey()).isEqualTo(new ScheduledTaskKey("opencode-runtime.stale-active-run-reconcile"));
        assertThat(handler.cronExpression()).isEqualTo("0 */5 * * * *");
        assertThat(handler.lockTtl()).isEqualTo(java.time.Duration.ofMinutes(5));
    }

    @Test
    void skipsCronCatchUpScheduledBeforeJvmStart() {
        StaleActiveRunReconcileService service = mock(StaleActiveRunReconcileService.class);
        StaleActiveRunReconcileTaskHandler handler = new StaleActiveRunReconcileTaskHandler(
                service,
                Clock.fixed(STARTED_AT, ZoneOffset.UTC));

        ScheduledTaskResult result = handler.run(context(ScheduledTaskTriggerType.CRON, STARTED_AT.minusSeconds(1)));

        assertThat(result.result()).containsEntry("startupCatchUpSkipped", true);
        verify(service, never()).reconcile(any(), any());
    }

    @Test
    void manualTriggerRunsEvenWhenScheduledFireAtIsBeforeJvmStart() {
        StaleActiveRunReconcileService service = mock(StaleActiveRunReconcileService.class);
        when(service.reconcile(eq(TRACE_ID), any())).thenReturn(new StaleActiveRunReconcileService.Result(
                2,
                1,
                0,
                0,
                0,
                1));
        StaleActiveRunReconcileTaskHandler handler = new StaleActiveRunReconcileTaskHandler(
                service,
                Clock.fixed(STARTED_AT, ZoneOffset.UTC));

        ScheduledTaskResult result = handler.run(context(ScheduledTaskTriggerType.MANUAL, STARTED_AT.minusSeconds(60)));

        assertThat(result.result()).containsEntry("failedCount", 1);
        verify(service).reconcile(eq(TRACE_ID), any());
    }

    private static ScheduledTaskContext context(ScheduledTaskTriggerType triggerType, Instant scheduledFireAt) {
        return new ScheduledTaskContext(
                new ScheduledTaskRunId("str_stale_task1234567890"),
                new ScheduledTaskKey("opencode-runtime.stale-active-run-reconcile"),
                null,
                triggerType,
                null,
                scheduledFireAt,
                TRACE_ID,
                Map.of());
    }
}
