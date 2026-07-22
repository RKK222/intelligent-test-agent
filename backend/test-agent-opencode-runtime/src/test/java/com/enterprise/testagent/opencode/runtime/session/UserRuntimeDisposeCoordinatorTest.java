package com.enterprise.testagent.opencode.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.session.SessionRuntimeStateSummary;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.scheduler.VirtualTimeScheduler;

class UserRuntimeDisposeCoordinatorTest {

    private static final UserId USER_ID = new UserId("usr_runtime_dispose");
    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    @Test
    void rejectsDisposeWhenAtomicUserGateFindsAnotherActiveRun() {
        RunRuntimeStore store = Mockito.mock(RunRuntimeStore.class);
        SessionRuntimeStateApplicationService state = Mockito.mock(SessionRuntimeStateApplicationService.class);
        when(store.tryAcquireUserRuntimeDispose(eq(USER_ID), anyString(), any(Duration.class))).thenReturn(false);
        UserRuntimeDisposeCoordinator coordinator = new UserRuntimeDisposeCoordinator(store, state);
        AtomicBoolean called = new AtomicBoolean();

        assertThatThrownBy(() -> coordinator.withUserIdle(USER_ID, "trace_busy", () -> {
            called.set(true);
            return "unexpected";
        }))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("运行中的 Session");

        assertThat(called).isFalse();
        verify(state, never()).snapshot(any());
        verify(store, never()).releaseUserRuntimeDispose(any(), anyString());
    }

    @Test
    void rechecksCompatibilitySessionSnapshotAndReleasesGateAfterIdleAction() {
        RunRuntimeStore store = Mockito.mock(RunRuntimeStore.class);
        SessionRuntimeStateApplicationService state = Mockito.mock(SessionRuntimeStateApplicationService.class);
        when(store.tryAcquireUserRuntimeDispose(eq(USER_ID), anyString(), any(Duration.class))).thenReturn(true);
        when(state.snapshot(USER_ID)).thenReturn(new SessionRuntimeStateSummary(0, 0, 0, List.of(), NOW));
        UserRuntimeDisposeCoordinator coordinator = new UserRuntimeDisposeCoordinator(store, state);

        String result = coordinator.withUserIdle(USER_ID, "trace_idle", () -> "disposed");

        assertThat(result).isEqualTo("disposed");
        verify(store).releaseUserRuntimeDispose(eq(USER_ID), anyString());
    }

    @Test
    void rejectsRunStartWhileDisposeGateIsHeld() {
        RunRuntimeStore store = Mockito.mock(RunRuntimeStore.class);
        SessionRuntimeStateApplicationService state = Mockito.mock(SessionRuntimeStateApplicationService.class);
        when(store.isUserRuntimeDisposeActive(USER_ID)).thenReturn(true);
        UserRuntimeDisposeCoordinator coordinator = new UserRuntimeDisposeCoordinator(store, state);

        assertThatThrownBy(() -> coordinator.requireNotDisposing(USER_ID, "trace_start"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("运行中的 Session");
    }

    @Test
    void renewsDisposeLeaseWhileRemoteCallIsStillRunning() {
        RunRuntimeStore store = Mockito.mock(RunRuntimeStore.class);
        SessionRuntimeStateApplicationService state = Mockito.mock(SessionRuntimeStateApplicationService.class);
        Duration ttl = Duration.ofMinutes(2);
        Duration renewInterval = Duration.ofSeconds(30);
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        when(store.tryAcquireUserRuntimeDispose(eq(USER_ID), anyString(), eq(ttl))).thenReturn(true);
        when(store.renewUserRuntimeDispose(eq(USER_ID), anyString(), eq(ttl))).thenReturn(true);
        when(state.snapshot(USER_ID)).thenReturn(new SessionRuntimeStateSummary(0, 0, 0, List.of(), NOW));
        UserRuntimeDisposeCoordinator coordinator = new UserRuntimeDisposeCoordinator(
                store, state, ttl, renewInterval, scheduler);

        String result = coordinator.withUserIdle(USER_ID, "trace_renew", () -> {
            scheduler.advanceTimeBy(renewInterval);
            return "disposed";
        });

        assertThat(result).isEqualTo("disposed");
        verify(store).renewUserRuntimeDispose(eq(USER_ID), anyString(), eq(ttl));
        verify(store).releaseUserRuntimeDispose(eq(USER_ID), anyString());
    }

    @Test
    void reportsConflictWhenDisposeLeaseIsLostDuringRemoteCall() {
        RunRuntimeStore store = Mockito.mock(RunRuntimeStore.class);
        SessionRuntimeStateApplicationService state = Mockito.mock(SessionRuntimeStateApplicationService.class);
        Duration ttl = Duration.ofMinutes(2);
        Duration renewInterval = Duration.ofSeconds(30);
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        when(store.tryAcquireUserRuntimeDispose(eq(USER_ID), anyString(), eq(ttl))).thenReturn(true);
        when(store.renewUserRuntimeDispose(eq(USER_ID), anyString(), eq(ttl))).thenReturn(false);
        when(state.snapshot(USER_ID)).thenReturn(new SessionRuntimeStateSummary(0, 0, 0, List.of(), NOW));
        UserRuntimeDisposeCoordinator coordinator = new UserRuntimeDisposeCoordinator(
                store, state, ttl, renewInterval, scheduler);

        assertThatThrownBy(() -> coordinator.withUserIdle(USER_ID, "trace_lost", () -> {
            scheduler.advanceTimeBy(renewInterval);
            return "ambiguous";
        }))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("闸门已失效");

        verify(store).releaseUserRuntimeDispose(eq(USER_ID), anyString());
    }
}
