package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunTerminalProjectionPending;
import com.enterprise.testagent.domain.run.RunTerminalProjectionResult;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证 Java crash 后只由公共路由选中的同服务器 Java 补做终态投影。 */
class RunTerminalProjectionRecoveryCoordinatorTest {

    private static final RunId RUN_ID = new RunId("run_terminal_outbox_recovery");
    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

    @Test
    void projectsPendingOutboxOnSelectedBackendAfterTerminalAppendCrash() {
        Fixture fixture = fixture();
        when(fixture.projectionService.project(fixture.pending))
                .thenReturn(RunTerminalProjectionResult.APPLIED);

        RunTerminalProjectionRecoveryCoordinator.Result result = fixture.coordinator.recoverCurrentServer(
                "trace_terminal_recovery", () -> false);

        assertThat(result.appliedCount()).isEqualTo(1);
        verify(fixture.projectionService).project(fixture.pending);
    }

    @Test
    void backendSelectedForAnotherJavaNeverProjectsPendingOutbox() {
        Fixture fixture = fixture();
        when(fixture.routeResolver.remoteTarget("server-a")).thenReturn(Optional.of("server-a"));

        RunTerminalProjectionRecoveryCoordinator.Result result = fixture.coordinator.recoverCurrentServer(
                "trace_terminal_recovery", () -> false);

        assertThat(result.routedAwayCount()).isEqualTo(1);
        verifyNoInteractions(fixture.projectionService);
        verify(fixture.store, never()).ackTerminalProjection(RUN_ID, 7L);
    }

    @Test
    void pendingDatabaseProjectionRemainsRecoverable() {
        Fixture fixture = fixture();
        when(fixture.projectionService.project(fixture.pending))
                .thenReturn(RunTerminalProjectionResult.TERMINAL_PENDING_DB);

        RunTerminalProjectionRecoveryCoordinator.Result result = fixture.coordinator.recoverCurrentServer(
                "trace_terminal_recovery", () -> false);

        assertThat(result.pendingDatabaseCount()).isEqualTo(1);
        verify(fixture.projectionService).project(fixture.pending);
        verify(fixture.store, never()).ackTerminalProjection(RUN_ID, 7L);
    }

    @Test
    void oneRoutingFailureDoesNotAbortRemainingOutboxCandidates() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunTerminalProjectionService projectionService = mock(RunTerminalProjectionService.class);
        RunTerminalProjectionPending first = pending();
        RunTerminalProjectionPending second = new RunTerminalProjectionPending(
                new RunId("run_terminal_outbox_recovery_second"),
                "server-a",
                8L,
                RunStatus.SUCCEEDED,
                "REMOTE_ROOT",
                "COMPLETED",
                null,
                false,
                "trace_terminal_event_second",
                NOW.plusSeconds(1));
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.remoteTarget("server-a"))
                .thenThrow(new IllegalStateException("route unavailable"))
                .thenReturn(Optional.empty());
        when(store.findTerminalProjectionPendingByServer("server-a", 200)).thenReturn(List.of(first, second));
        when(projectionService.project(second)).thenReturn(RunTerminalProjectionResult.APPLIED);
        RunTerminalProjectionRecoveryCoordinator coordinator = new RunTerminalProjectionRecoveryCoordinator(
                store, routeResolver, projectionService);

        RunTerminalProjectionRecoveryCoordinator.Result result = coordinator.recoverCurrentServer(
                "trace_terminal_recovery", () -> false);

        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.appliedCount()).isEqualTo(1);
        verify(projectionService).project(second);
    }

    private Fixture fixture() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunTerminalProjectionService projectionService = mock(RunTerminalProjectionService.class);
        RunTerminalProjectionPending pending = pending();
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.remoteTarget("server-a")).thenReturn(Optional.empty());
        when(store.findTerminalProjectionPendingByServer("server-a", 200)).thenReturn(List.of(pending));
        return new Fixture(
                store,
                routeResolver,
                projectionService,
                pending,
                new RunTerminalProjectionRecoveryCoordinator(store, routeResolver, projectionService));
    }

    private RunTerminalProjectionPending pending() {
        return new RunTerminalProjectionPending(
                RUN_ID,
                "server-a",
                7L,
                RunStatus.SUCCEEDED,
                "REMOTE_ROOT",
                "COMPLETED",
                null,
                false,
                "trace_terminal_event",
                NOW);
    }

    private record Fixture(
            RunRuntimeStore store,
            BackendJavaRouteResolver routeResolver,
            RunTerminalProjectionService projectionService,
            RunTerminalProjectionPending pending,
            RunTerminalProjectionRecoveryCoordinator coordinator) { }
}
