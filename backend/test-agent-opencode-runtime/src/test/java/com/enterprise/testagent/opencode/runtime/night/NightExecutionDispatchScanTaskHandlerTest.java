package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.opencode.runtime.run.RunResendDispatchCoordinator;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NightExecutionDispatchScanTaskHandlerTest {

    @Test
    void registeredNightScanAlsoDispatchesDueResends() {
        NightExecutionDispatchCoordinator night = mock(NightExecutionDispatchCoordinator.class);
        RunResendDispatchCoordinator resend = mock(RunResendDispatchCoordinator.class);
        when(night.dispatchDue(eq("trace_resend_scan"), notNull()))
                .thenReturn(new NightExecutionDispatchCoordinator.Result(2, 1, 1, 2, 0));
        when(resend.dispatchDue(eq("trace_resend_scan"), notNull()))
                .thenReturn(new RunResendDispatchCoordinator.Result(1, 1, 1, 1, 0));
        NightExecutionDispatchScanTaskHandler handler =
                new NightExecutionDispatchScanTaskHandler(night, resend);

        var result = handler.run(new ScheduledTaskContext(
                new ScheduledTaskRunId("str_resend_scan1234567890"),
                NightExecutionDispatchScanTaskHandler.TASK_KEY,
                null,
                ScheduledTaskTriggerType.CRON,
                null,
                Instant.parse("2026-08-07T10:00:00Z"),
                "trace_resend_scan",
                Map.of()));

        assertThat(result.result()).containsEntry("started", 2).containsEntry("resendStarted", 1);
        verify(night).dispatchDue(eq("trace_resend_scan"), notNull());
        verify(resend).dispatchDue(eq("trace_resend_scan"), notNull());
    }
}
