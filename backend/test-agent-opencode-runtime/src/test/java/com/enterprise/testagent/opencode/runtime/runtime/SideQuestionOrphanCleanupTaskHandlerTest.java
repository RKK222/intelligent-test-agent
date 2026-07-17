package com.enterprise.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证旁路孤儿回收任务复用 scheduler 的五分钟 Cron、分布式锁与协作式停止。 */
class SideQuestionOrphanCleanupTaskHandlerTest {

    private static final String TRACE_ID = "trace_sideorphanhandler01";

    @Test
    void exposesFiveMinuteCronAndSchedulerLock() {
        SideQuestionOrphanCleanupTaskHandler handler =
                new SideQuestionOrphanCleanupTaskHandler(mock(SideQuestionOrphanCleanupService.class));

        assertThat(handler.taskKey())
                .isEqualTo(new ScheduledTaskKey("opencode-runtime.side-question-orphan-cleanup"));
        assertThat(handler.cronExpression()).isEqualTo("0 */5 * * * *");
        assertThat(handler.lockTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void forwardsStopRequestedSupplierAndReturnsStructuredCounts() {
        SideQuestionOrphanCleanupService service = mock(SideQuestionOrphanCleanupService.class);
        when(service.cleanup(eq(TRACE_ID), any())).thenReturn(
                new SideQuestionOrphanCleanupService.Result(2, 0, 0, 0, 0, 1, false));
        SideQuestionOrphanCleanupTaskHandler handler = new SideQuestionOrphanCleanupTaskHandler(service);
        AtomicBoolean stop = new AtomicBoolean(false);
        ScheduledTaskContext context = context(stop);

        var result = handler.run(context);

        assertThat(result.result()).containsEntry("cleanedCount", 1);
        ArgumentCaptor<BooleanSupplier> supplier = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(service).cleanup(eq(TRACE_ID), supplier.capture());
        assertThat(supplier.getValue().getAsBoolean()).isFalse();
        stop.set(true);
        assertThat(supplier.getValue().getAsBoolean()).isTrue();
    }

    private ScheduledTaskContext context(AtomicBoolean stop) {
        return new ScheduledTaskContext(
                new ScheduledTaskRunId("str_sideorphanhandler01"),
                new ScheduledTaskKey("opencode-runtime.side-question-orphan-cleanup"),
                null,
                ScheduledTaskTriggerType.CRON,
                null,
                Instant.parse("2026-07-11T04:00:00Z"),
                TRACE_ID,
                Map.of(),
                stop::get);
    }
}
