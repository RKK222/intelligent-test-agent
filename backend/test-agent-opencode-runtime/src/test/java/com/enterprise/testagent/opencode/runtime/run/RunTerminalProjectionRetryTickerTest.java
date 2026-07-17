package com.enterprise.testagent.opencode.runtime.run;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.scheduler.ScheduledTaskLock;
import com.enterprise.testagent.scheduler.ScheduledTaskLockLease;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunTerminalProjectionRetryTickerTest {

    @Test
    void dueTickUsesTheSameGlobalLockAsTheManagedTask() {
        RunTerminalProjectionRetryService retryService = mock(RunTerminalProjectionRetryService.class);
        ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
        ScheduledTaskLockLease lease = mock(ScheduledTaskLockLease.class);
        when(taskLock.acquire(
                        RunTerminalProjectionRetryTaskHandler.TASK_KEY,
                        RunTerminalProjectionRetryTaskHandler.LOCK_TTL))
                .thenReturn(Optional.of(lease));
        RunTerminalProjectionRetryTicker ticker = new RunTerminalProjectionRetryTicker(retryService, taskLock);

        ticker.tick();

        verify(retryService).retryDue(org.mockito.ArgumentMatchers.any());
        verify(lease).release();
    }

    @Test
    void busyGlobalLockSkipsRetryWithoutTouchingPostgresqlService() {
        RunTerminalProjectionRetryService retryService = mock(RunTerminalProjectionRetryService.class);
        ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
        when(taskLock.acquire(
                        RunTerminalProjectionRetryTaskHandler.TASK_KEY,
                        RunTerminalProjectionRetryTaskHandler.LOCK_TTL))
                .thenReturn(Optional.empty());
        RunTerminalProjectionRetryTicker ticker = new RunTerminalProjectionRetryTicker(retryService, taskLock);

        ticker.tick();

        verify(retryService, never()).retryDue(org.mockito.ArgumentMatchers.any());
    }
}
