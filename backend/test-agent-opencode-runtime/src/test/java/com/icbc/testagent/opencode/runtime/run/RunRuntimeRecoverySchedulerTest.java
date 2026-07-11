package com.icbc.testagent.opencode.runtime.run;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RunRuntimeRecoverySchedulerTest {

    @Test
    void startupRecoversTerminalOutboxBeforeActiveRuns() {
        RunRuntimeRecoveryCoordinator active = mock(RunRuntimeRecoveryCoordinator.class);
        RunTerminalProjectionRecoveryCoordinator terminal = mock(RunTerminalProjectionRecoveryCoordinator.class);
        RunRuntimeRecoveryScheduler scheduler = new RunRuntimeRecoveryScheduler(active, terminal);

        scheduler.recoverOnStartup();

        InOrder order = inOrder(terminal, active);
        order.verify(terminal).recoverCurrentServer(any(), any());
        order.verify(active).recoverCurrentServer(any(), any());
    }

    @Test
    void terminalRecoveryFailureDoesNotSuppressActiveRecovery() {
        RunRuntimeRecoveryCoordinator active = mock(RunRuntimeRecoveryCoordinator.class);
        RunTerminalProjectionRecoveryCoordinator terminal = mock(RunTerminalProjectionRecoveryCoordinator.class);
        when(terminal.recoverCurrentServer(any(), any())).thenThrow(new IllegalStateException("redis unavailable"));
        RunRuntimeRecoveryScheduler scheduler = new RunRuntimeRecoveryScheduler(active, terminal);

        scheduler.recoverPeriodically();

        verify(active).recoverCurrentServer(any(), any());
    }
}
