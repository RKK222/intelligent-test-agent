package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunOwnerLease;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RunRuntimeRecoveryCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-07-11T06:00:00Z");
    private static final RunId RUN_ID = new RunId("run_recovery_owner");

    @Test
    void resumesOnlyAfterDispatchMessageIsConfirmedAccepted() {
        Fixture fixture = fixture(RunDispatchAcceptance.ACCEPTED);
        when(fixture.executor.resumeAcceptedRun(
                fixture.manifest, fixture.lease, "trace_recovery")).thenReturn(true);

        RunRuntimeRecoveryCoordinator.Result result = fixture.coordinator.recoverCurrentServer(
                "trace_recovery", () -> false);

        assertThat(result.resumedCount()).isEqualTo(1);
        ArgumentCaptor<RunDispatchProbeRequest> request = ArgumentCaptor.forClass(RunDispatchProbeRequest.class);
        verify(fixture.probe).probe(request.capture());
        assertThat(request.getValue().dispatchMessageId()).isEqualTo("msg_dispatch_recovery");
        assertThat(request.getValue().remoteSessionId()).isEqualTo("remote-recovery");
        verify(fixture.store, never()).releaseOwnerLease(fixture.lease);
    }

    @Test
    void unknownAcceptanceNeverResendsAndReleasesLeaseForLaterRetry() {
        Fixture fixture = fixture(RunDispatchAcceptance.UNKNOWN);

        RunRuntimeRecoveryCoordinator.Result result = fixture.coordinator.recoverCurrentServer(
                "trace_recovery", () -> false);

        assertThat(result.unknownSkippedCount()).isEqualTo(1);
        verifyNoInteractions(fixture.executor);
        verify(fixture.store, never()).claimOwnerLeaseIfUnchanged(any(), any());
        verify(fixture.store, never()).releaseOwnerLease(any());
    }

    @Test
    void runSelectedForAnotherJavaIsNeverClaimed() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunDispatchAcceptanceProbe probe = mock(RunDispatchAcceptanceProbe.class);
        RunRecoveryTakeoverExecutor executor = mock(RunRecoveryTakeoverExecutor.class);
        RunRuntimeManifest manifest = manifest("server-a");
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routeResolver.remoteTarget("server-a")).thenReturn(Optional.of("server-a"));
        when(store.findActiveByServer("server-a")).thenReturn(List.of(manifest));
        when(store.findInput(RUN_ID)).thenReturn(Optional.of(runtimeInput()));
        RunRuntimeRecoveryCoordinator coordinator = new RunRuntimeRecoveryCoordinator(
                store, routeResolver, probe, executor);

        RunRuntimeRecoveryCoordinator.Result result = coordinator.recoverCurrentServer(
                "trace_recovery", () -> false);

        assertThat(result.routedAwayCount()).isEqualTo(1);
        verify(store, never()).claimOwnerLeaseIfUnchanged(any(), any());
        verifyNoInteractions(probe, executor);
    }

    @Test
    void currentJvmOwnedTakeoverIsNotReadoptedByPeriodicScan() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunDispatchAcceptanceProbe probe = mock(RunDispatchAcceptanceProbe.class);
        RunRecoveryTakeoverExecutor executor = mock(RunRecoveryTakeoverExecutor.class);
        RunOwnerLeaseSupervisor supervisor = mock(RunOwnerLeaseSupervisor.class);
        RunRuntimeManifest manifest = manifest("server-a");
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(store.findActiveByServer("server-a")).thenReturn(List.of(manifest));
        when(supervisor.isOwned(RUN_ID)).thenReturn(true);
        RunRuntimeRecoveryCoordinator coordinator = new RunRuntimeRecoveryCoordinator(
                store, routeResolver, probe, executor, supervisor);

        RunRuntimeRecoveryCoordinator.Result result = coordinator.recoverCurrentServer(
                "trace_recovery", () -> false);

        assertThat(result.currentOwnerSkippedCount()).isEqualTo(1);
        verify(store, never()).claimOwnerLeaseIfUnchanged(any(), any());
        verifyNoInteractions(probe, executor);
    }

    @Test
    void definitivelyMissingDispatchIsFencedAndConvergedWithoutResend() {
        Fixture fixture = fixture(RunDispatchAcceptance.NOT_ACCEPTED);
        when(fixture.executor.failUnacceptedRun(
                fixture.manifest, fixture.lease, "trace_recovery")).thenReturn(true);

        RunRuntimeRecoveryCoordinator.Result result = fixture.coordinator.recoverCurrentServer(
                "trace_recovery", () -> false);

        assertThat(result.notAcceptedConvergedCount()).isEqualTo(1);
        verify(fixture.executor, never()).resumeAcceptedRun(any(), any(), eq("trace_recovery"));
        verify(fixture.executor).failUnacceptedRun(
                fixture.manifest, fixture.lease, "trace_recovery");
        verify(fixture.store).claimOwnerLeaseIfUnchanged(fixture.manifest, "bjp_current");
        verify(fixture.store, never()).releaseOwnerLease(any());
    }

    @Test
    void initializedManifestWithoutAnchorIsDiscardedAfterCrashGrace() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunDispatchAcceptanceProbe probe = mock(RunDispatchAcceptanceProbe.class);
        RunRecoveryTakeoverExecutor executor = mock(RunRecoveryTakeoverExecutor.class);
        RunRepository repository = mock(RunRepository.class);
        RunRuntimeManifest manifest = manifest("server-a");
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routeResolver.remoteTarget("server-a")).thenReturn(Optional.empty());
        when(store.findActiveByServer("server-a")).thenReturn(List.of(manifest));
        when(repository.findById(RUN_ID)).thenReturn(Optional.empty());
        RunRuntimeRecoveryCoordinator coordinator = new RunRuntimeRecoveryCoordinator(
                store,
                routeResolver,
                probe,
                executor,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RunRuntimeRecoveryCoordinator.Result result = coordinator.recoverCurrentServer(
                "trace_recovery", () -> false);

        assertThat(result.orphanManifestDiscardedCount()).isEqualTo(1);
        verify(store).discardBeforeDispatch(RUN_ID);
        verifyNoInteractions(probe, executor);
    }

    @Test
    void databaseTerminalAnchorStopsRedisManifestFromBeingResumed() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunDispatchAcceptanceProbe probe = mock(RunDispatchAcceptanceProbe.class);
        RunRecoveryTakeoverExecutor executor = mock(RunRecoveryTakeoverExecutor.class);
        RunRepository repository = mock(RunRepository.class);
        RunRuntimeManifest manifest = manifest("server-a");
        RunOwnerLease lease = new RunOwnerLease(RUN_ID, "bjp_current", 8L, NOW.plusSeconds(15));
        Run persisted = new Run(
                RUN_ID,
                manifest.sessionId(),
                manifest.workspaceId(),
                RunStatus.FAILED,
                manifest.createdAt(),
                NOW,
                "trace_terminal");
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routeResolver.remoteTarget("server-a")).thenReturn(Optional.empty());
        when(store.findActiveByServer("server-a")).thenReturn(List.of(manifest));
        when(store.claimOwnerLeaseIfUnchanged(manifest, "bjp_current")).thenReturn(Optional.of(lease));
        when(repository.findById(RUN_ID)).thenReturn(Optional.of(persisted));
        RunRuntimeRecoveryCoordinator coordinator = new RunRuntimeRecoveryCoordinator(
                store,
                routeResolver,
                probe,
                executor,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RunRuntimeRecoveryCoordinator.Result result = coordinator.recoverCurrentServer(
                "trace_recovery", () -> false);

        assertThat(result.databaseTerminalConvergedCount()).isEqualTo(1);
        verify(store).updateStatus(RUN_ID, RunStatus.FAILED, manifest.statusVersion(), null);
        verify(store).releaseOwnerLease(lease);
        verifyNoInteractions(probe, executor);
    }

    @Test
    void remoteFinalMessageStopsRedisManifestBeforeRecoverySubscription() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunDispatchAcceptanceProbe probe = mock(RunDispatchAcceptanceProbe.class);
        RunRecoveryTakeoverExecutor executor = mock(RunRecoveryTakeoverExecutor.class);
        RunRepository repository = mock(RunRepository.class);
        RunApplicationService runApplicationService = mock(RunApplicationService.class);
        RunRuntimeManifest manifest = manifest("server-a");
        Run persisted = new Run(
                RUN_ID,
                manifest.sessionId(),
                manifest.workspaceId(),
                RunStatus.RUNNING,
                manifest.createdAt(),
                NOW,
                "trace_recovery");
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routeResolver.remoteTarget("server-a")).thenReturn(Optional.empty());
        when(store.findActiveByServer("server-a")).thenReturn(List.of(manifest));
        when(repository.findById(RUN_ID)).thenReturn(Optional.of(persisted));
        when(runApplicationService.findActiveRun(manifest.sessionId())).thenReturn(Optional.empty());
        RunRuntimeRecoveryCoordinator coordinator = new RunRuntimeRecoveryCoordinator(
                store,
                routeResolver,
                probe,
                executor,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                runApplicationService);

        RunRuntimeRecoveryCoordinator.Result result = coordinator.recoverCurrentServer(
                "trace_recovery", () -> false);

        assertThat(result.databaseTerminalConvergedCount()).isEqualTo(1);
        verify(runApplicationService).findActiveRun(manifest.sessionId());
        verifyNoInteractions(probe, executor);
    }

    private Fixture fixture(RunDispatchAcceptance acceptance) {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunDispatchAcceptanceProbe probe = mock(RunDispatchAcceptanceProbe.class);
        RunRecoveryTakeoverExecutor executor = mock(RunRecoveryTakeoverExecutor.class);
        RunRuntimeManifest manifest = manifest("server-a");
        RunOwnerLease lease = new RunOwnerLease(RUN_ID, "bjp_current", 7L, NOW.plusSeconds(15));
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routeResolver.remoteTarget("server-a")).thenReturn(Optional.empty());
        when(store.findActiveByServer("server-a")).thenReturn(List.of(manifest));
        when(store.findInput(RUN_ID)).thenReturn(Optional.of(runtimeInput()));
        when(store.claimOwnerLeaseIfUnchanged(manifest, "bjp_current")).thenReturn(Optional.of(lease));
        when(probe.probe(any())).thenReturn(acceptance);
        return new Fixture(
                store, probe, executor, manifest, lease,
                new RunRuntimeRecoveryCoordinator(store, routeResolver, probe, executor));
    }

    private static RunRuntimeManifest manifest(String serverId) {
        return new RunRuntimeManifest(
                RUN_ID, RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_recovery"), new SessionId("ses_recovery"),
                new WorkspaceId("wrk_recovery"), "opencode", "req_recovery",
                "msg_dispatch_recovery", serverId, "bjp_original", "node_recovery", "ocp_recovery",
                "remote-recovery", RunStatus.RUNNING,
                2L, 10L, 1L, 0L, false, 10L, 1024L,
                null, null, null, NOW.plusSeconds(3600), NOW.minusSeconds(60), NOW.minusSeconds(10));
    }

    private static RunRuntimeInput runtimeInput() {
        return new RunRuntimeInput(
                RUN_ID, "prompt", List.of(), "msg_dispatch_recovery", NOW,
                "/trusted/workspace", "http://127.0.0.1:4096");
    }

    private record Fixture(
            RunRuntimeStore store,
            RunDispatchAcceptanceProbe probe,
            RunRecoveryTakeoverExecutor executor,
            RunRuntimeManifest manifest,
            RunOwnerLease lease,
            RunRuntimeRecoveryCoordinator coordinator) { }
}
