package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunOwnerLease;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunPendingAskExpiryCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-07-11T06:00:00Z");
    private static final RunId RUN_ID = new RunId("run_pending_expiry");

    @Test
    void physicalPendingAskRetentionLeavesAtLeastOneExpiryScanWindow() {
        assertThat(RunRuntimeStore.PENDING_ASK_RECOVERY_BUFFER)
                .isGreaterThanOrEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void expiresPendingAskExactlyAtSevenDayBoundaryUsingFixedClock() {
        Fixture fixture = fixture(manifest(
                RunStorageMode.REDIS_SUMMARY,
                RunStatus.RUNNING,
                "question",
                NOW.minus(RunRuntimeStore.PENDING_ASK_TTL)));
        when(fixture.executor.expirePendingAsk(
                fixture.manifest, fixture.lease, "trace_pending_expiry")).thenReturn(true);

        RunPendingAskExpiryCoordinator.Result result = fixture.coordinator.expireCurrentServer(
                "trace_pending_expiry", () -> false);

        assertThat(result.expiredCount()).isEqualTo(1);
        verify(fixture.executor).expirePendingAsk(
                fixture.manifest, fixture.lease, "trace_pending_expiry");
        verify(fixture.store, never()).releaseOwnerLease(fixture.lease);
    }

    @Test
    void skipsPendingAskNewerThanSevenDaysAndAllIneligibleManifests() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunPendingAskExpiryExecutor executor = mock(RunPendingAskExpiryExecutor.class);
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(store.findActiveByServer("server-a")).thenReturn(List.of(
                manifest(RunStorageMode.REDIS_SUMMARY, RunStatus.RUNNING, "permission",
                        NOW.minus(RunRuntimeStore.PENDING_ASK_TTL).plusNanos(1)),
                manifest(RunStorageMode.REDIS_SUMMARY, RunStatus.RUNNING, " ",
                        NOW.minus(RunRuntimeStore.PENDING_ASK_TTL)),
                manifest(RunStorageMode.REDIS_SUMMARY, RunStatus.SUCCEEDED, "question",
                        NOW.minus(RunRuntimeStore.PENDING_ASK_TTL)),
                manifest(RunStorageMode.LEGACY_FULL, RunStatus.RUNNING, "question",
                        NOW.minus(RunRuntimeStore.PENDING_ASK_TTL))));
        RunPendingAskExpiryCoordinator coordinator = new RunPendingAskExpiryCoordinator(
                store, routeResolver, executor, Clock.fixed(NOW, ZoneOffset.UTC));

        RunPendingAskExpiryCoordinator.Result result = coordinator.expireCurrentServer(
                "trace_pending_expiry", () -> false);

        assertThat(result.scannedCount()).isEqualTo(4);
        assertThat(result.ineligibleSkippedCount()).isEqualTo(4);
        verify(store, never()).claimOwnerLeaseIfUnchanged(any(), any());
        verifyNoInteractions(executor);
    }

    @Test
    void runSelectedForAnotherJavaOnSameServerIsNeverClaimed() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunPendingAskExpiryExecutor executor = mock(RunPendingAskExpiryExecutor.class);
        RunRuntimeManifest manifest = expiredManifest();
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routeResolver.remoteTarget("server-a")).thenReturn(Optional.of("server-a"));
        when(store.findActiveByServer("server-a")).thenReturn(List.of(manifest));
        RunPendingAskExpiryCoordinator coordinator = new RunPendingAskExpiryCoordinator(
                store, routeResolver, executor, Clock.fixed(NOW, ZoneOffset.UTC));

        RunPendingAskExpiryCoordinator.Result result = coordinator.expireCurrentServer(
                "trace_pending_expiry", () -> false);

        assertThat(result.routedAwayCount()).isEqualTo(1);
        verify(store, never()).claimOwnerLeaseIfUnchanged(any(), any());
        verifyNoInteractions(executor);
    }

    @Test
    void leaseBusyRunIsLeftForCurrentOwner() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunPendingAskExpiryExecutor executor = mock(RunPendingAskExpiryExecutor.class);
        RunRuntimeManifest manifest = expiredManifest();
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routeResolver.remoteTarget("server-a")).thenReturn(Optional.empty());
        when(store.findActiveByServer("server-a")).thenReturn(List.of(manifest));
        when(store.claimOwnerLeaseIfUnchanged(manifest, "bjp_current")).thenReturn(Optional.empty());
        RunPendingAskExpiryCoordinator coordinator = new RunPendingAskExpiryCoordinator(
                store, routeResolver, executor, Clock.fixed(NOW, ZoneOffset.UTC));

        RunPendingAskExpiryCoordinator.Result result = coordinator.expireCurrentServer(
                "trace_pending_expiry", () -> false);

        assertThat(result.leaseBusyCount()).isEqualTo(1);
        verifyNoInteractions(executor);
        verify(store, never()).releaseOwnerLease(any());
    }

    @Test
    void executorThatDoesNotTakeOverReleasesExactFencedLease() {
        Fixture fixture = fixture(expiredManifest());
        when(fixture.executor.expirePendingAsk(
                fixture.manifest, fixture.lease, "trace_pending_expiry")).thenReturn(false);

        RunPendingAskExpiryCoordinator.Result result = fixture.coordinator.expireCurrentServer(
                "trace_pending_expiry", () -> false);

        assertThat(result.executorUnavailableCount()).isEqualTo(1);
        verify(fixture.store).releaseOwnerLease(fixture.lease);
    }

    @Test
    void executorFailureReleasesExactFencedLease() {
        Fixture fixture = fixture(expiredManifest());
        when(fixture.executor.expirePendingAsk(
                fixture.manifest, fixture.lease, "trace_pending_expiry"))
                .thenThrow(new IllegalStateException("sensitive remote response"));

        RunPendingAskExpiryCoordinator.Result result = fixture.coordinator.expireCurrentServer(
                "trace_pending_expiry", () -> false);

        assertThat(result.failedCount()).isEqualTo(1);
        verify(fixture.store).releaseOwnerLease(fixture.lease);
    }

    @Test
    void replyThatWinsBeforeLeaseSideEffectsPreventsExpiry() {
        Fixture fixture = fixture(expiredManifest());
        when(fixture.store.claimOwnerLeaseIfUnchanged(fixture.manifest, "bjp_current"))
                .thenReturn(Optional.empty());

        RunPendingAskExpiryCoordinator.Result result = fixture.coordinator.expireCurrentServer(
                "trace_pending_expiry", () -> false);

        assertThat(result.leaseBusyCount()).isEqualTo(1);
        verifyNoInteractions(fixture.executor);
        verify(fixture.store, never()).releaseOwnerLease(any());
    }

    private static Fixture fixture(RunRuntimeManifest manifest) {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunPendingAskExpiryExecutor executor = mock(RunPendingAskExpiryExecutor.class);
        RunOwnerLease lease = new RunOwnerLease(RUN_ID, "bjp_current", 17L, NOW.plusSeconds(15));
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routeResolver.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routeResolver.remoteTarget("server-a")).thenReturn(Optional.empty());
        when(store.findActiveByServer("server-a")).thenReturn(List.of(manifest));
        when(store.claimOwnerLeaseIfUnchanged(manifest, "bjp_current")).thenReturn(Optional.of(lease));
        return new Fixture(
                store,
                executor,
                manifest,
                lease,
                new RunPendingAskExpiryCoordinator(
                        store, routeResolver, executor, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static RunRuntimeManifest expiredManifest() {
        return manifest(
                RunStorageMode.REDIS_SUMMARY,
                RunStatus.RUNNING,
                "question",
                NOW.minus(RunRuntimeStore.PENDING_ASK_TTL));
    }

    private static RunRuntimeManifest manifest(
            RunStorageMode storageMode,
            RunStatus status,
            String attention,
            Instant attentionAt) {
        return new RunRuntimeManifest(
                RUN_ID,
                storageMode,
                new UserId("usr_pending_expiry"),
                new SessionId("ses_pending_expiry"),
                new WorkspaceId("wrk_pending_expiry"),
                "opencode",
                "req_pending_expiry",
                "msg_pending_expiry",
                "server-a",
                "bjp_original",
                "node_pending_expiry",
                "ocp_pending_expiry",
                "remote-pending-expiry",
                status,
                3L,
                10L,
                1L,
                0L,
                false,
                10L,
                1024L,
                attention,
                "evt_pending_expiry",
                attentionAt,
                NOW.plusSeconds(3600),
                NOW.minus(RunRuntimeStore.PENDING_ASK_TTL).minusSeconds(30),
                NOW.minusSeconds(30));
    }

    private record Fixture(
            RunRuntimeStore store,
            RunPendingAskExpiryExecutor executor,
            RunRuntimeManifest manifest,
            RunOwnerLease lease,
            RunPendingAskExpiryCoordinator coordinator) { }
}
