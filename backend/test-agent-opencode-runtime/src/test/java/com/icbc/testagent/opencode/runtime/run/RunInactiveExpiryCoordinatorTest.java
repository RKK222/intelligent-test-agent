package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunInactiveExpiryCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");

    @Test
    void expiresOnlyNonAttentionRunAtExactTwoHourBoundary() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        RunInactiveExpiryExecutor executor = mock(RunInactiveExpiryExecutor.class);
        RunRuntimeManifest stale = manifest("run_inactive_stale", NOW.minusSeconds(7_200), null);
        RunRuntimeManifest pending = manifest("run_inactive_pending", NOW.minusSeconds(7_201), "QUESTION");
        RunOwnerLease lease = new RunOwnerLease(stale.runId(), "bjp_current", 3L, NOW.plusSeconds(15));
        when(routes.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routes.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routes.remoteTarget("server-a")).thenReturn(Optional.empty());
        when(store.findActiveByServer("server-a")).thenReturn(List.of(stale, pending));
        when(store.claimOwnerLeaseIfUnchanged(stale, "bjp_current")).thenReturn(Optional.of(lease));
        when(executor.expireInactiveRun(stale, lease, "trace_inactive")).thenReturn(true);
        RunInactiveExpiryCoordinator coordinator = new RunInactiveExpiryCoordinator(
                store, routes, executor, Clock.fixed(NOW, ZoneOffset.UTC));

        RunInactiveExpiryCoordinator.Result result = coordinator.expireCurrentServer("trace_inactive");

        assertThat(result).isEqualTo(new RunInactiveExpiryCoordinator.Result(2, 1, 1, 0));
        verify(executor).expireInactiveRun(stale, lease, "trace_inactive");
    }

    @Test
    void freshEventThatWinsBeforeLeaseSideEffectsPreventsExpiry() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        RunInactiveExpiryExecutor executor = mock(RunInactiveExpiryExecutor.class);
        RunRuntimeManifest stale = manifest("run_inactive_race", NOW.minusSeconds(7_201), null);
        when(routes.currentLinuxServerIdValue()).thenReturn("server-a");
        when(routes.currentBackendProcessIdValue()).thenReturn("bjp_current");
        when(routes.remoteTarget("server-a")).thenReturn(Optional.empty());
        when(store.findActiveByServer("server-a")).thenReturn(List.of(stale));
        when(store.claimOwnerLeaseIfUnchanged(stale, "bjp_current")).thenReturn(Optional.empty());
        RunInactiveExpiryCoordinator coordinator = new RunInactiveExpiryCoordinator(
                store, routes, executor, Clock.fixed(NOW, ZoneOffset.UTC));

        RunInactiveExpiryCoordinator.Result result = coordinator.expireCurrentServer("trace_inactive");

        assertThat(result).isEqualTo(new RunInactiveExpiryCoordinator.Result(1, 0, 1, 0));
        verify(executor, never()).expireInactiveRun(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(store, never()).releaseOwnerLease(org.mockito.ArgumentMatchers.any());
    }

    private RunRuntimeManifest manifest(String runId, Instant updatedAt, String attention) {
        return new RunRuntimeManifest(
                new RunId(runId), RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_inactive"), new SessionId("ses_" + runId),
                new WorkspaceId("wrk_inactive"), "opencode", "req_" + runId,
                "msg_" + runId, "server-a", "bjp_current", "node_inactive",
                "ocp_inactive", "remote-inactive", RunStatus.RUNNING,
                1L, 1L, 1L, 0L, false, 1L, 128L,
                attention, attention == null ? null : "evt_attention",
                attention == null ? null : NOW.minusSeconds(7_201),
                NOW.plusSeconds(300), NOW.minusSeconds(8_000), updatedAt);
    }
}
