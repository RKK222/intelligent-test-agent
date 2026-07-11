package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class RunOwnerLeaseSupervisorTest {

    private static final Instant NOW = Instant.parse("2026-07-11T11:00:00Z");
    private static final RunId RUN_ID = new RunId("run_owner_supervisor");

    @Test
    void leaseRenewalUsesDedicatedScheduler() throws Exception {
        Scheduled scheduled = RunOwnerLeaseSupervisor.class
                .getDeclaredMethod("renewOwnedLeases")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.scheduler()).isEqualTo("runOwnerLeaseTaskScheduler");
    }

    @Test
    void renewalFailureTerminatesLostSignalWithErrorWithoutEscapingSchedulerTick() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        RunOwnerLease lease = lease(1L);
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(store.renewOwnerLease(lease))
                .thenReturn(Optional.of(lease))
                .thenThrow(failure);
        RunOwnerLeaseSupervisor supervisor = supervisor(store);
        RunOwnerLeaseSupervisor.OwnershipHandle handle = supervisor.adopt(lease).orElseThrow();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<Throwable> error = new AtomicReference<>();
        handle.lost().subscribe(ignored -> { }, error::set, () -> completed.set(true));

        supervisor.renewOwnedLeases();

        assertThat(supervisor.isOwned(RUN_ID)).isFalse();
        assertThat(completed).isFalse();
        assertThat(error.get()).isSameAs(failure);
    }

    @Test
    void fencingLossCompletesLostSignalWithoutError() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        RunOwnerLease lease = lease(11L);
        when(store.renewOwnerLease(lease))
                .thenReturn(Optional.of(lease))
                .thenReturn(Optional.empty());
        RunOwnerLeaseSupervisor supervisor = supervisor(store);
        RunOwnerLeaseSupervisor.OwnershipHandle handle = supervisor.adopt(lease).orElseThrow();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<Throwable> error = new AtomicReference<>();
        handle.lost().subscribe(ignored -> { }, error::set, () -> completed.set(true));

        supervisor.renewOwnedLeases();

        assertThat(supervisor.isOwned(RUN_ID)).isFalse();
        assertThat(completed).isTrue();
        assertThat(error.get()).isNull();
    }

    @Test
    void releaseFailureStillMarksLocalSubscriptionLost() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        RunOwnerLease lease = lease(2L);
        when(store.renewOwnerLease(lease)).thenReturn(Optional.of(lease));
        when(store.releaseOwnerLease(lease)).thenThrow(new IllegalStateException("redis unavailable"));
        RunOwnerLeaseSupervisor supervisor = supervisor(store);
        RunOwnerLeaseSupervisor.OwnershipHandle handle = supervisor.adopt(lease).orElseThrow();
        AtomicBoolean lost = lostSignal(handle);

        assertThatThrownBy(() -> supervisor.release(handle))
                .isInstanceOf(IllegalStateException.class);
        assertThat(supervisor.isOwned(RUN_ID)).isFalse();
        assertThat(lost).isTrue();
    }

    @Test
    void adoptingSameOwnerAndFenceReusesExistingHandle() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        RunOwnerLease lease = lease(3L);
        when(store.renewOwnerLease(lease)).thenReturn(Optional.of(lease));
        RunOwnerLeaseSupervisor supervisor = supervisor(store);

        RunOwnerLeaseSupervisor.OwnershipHandle first = supervisor.adopt(lease).orElseThrow();
        RunOwnerLeaseSupervisor.OwnershipHandle second = supervisor.adopt(lease).orElseThrow();

        assertThat(second).isSameAs(first);
    }

    @Test
    void adoptingSameFenceRenewalFailureErrorsExistingLostSignal() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        RunOwnerLease lease = lease(31L);
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(store.renewOwnerLease(lease))
                .thenReturn(Optional.of(lease))
                .thenThrow(failure);
        RunOwnerLeaseSupervisor supervisor = supervisor(store);
        RunOwnerLeaseSupervisor.OwnershipHandle handle = supervisor.adopt(lease).orElseThrow();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<Throwable> error = new AtomicReference<>();
        handle.lost().subscribe(ignored -> { }, error::set, () -> completed.set(true));

        assertThatThrownBy(() -> supervisor.adopt(lease)).isSameAs(failure);

        assertThat(supervisor.isOwned(RUN_ID)).isFalse();
        assertThat(completed).isFalse();
        assertThat(error.get()).isSameAs(failure);
    }

    @Test
    void releasingReplacedHandleCannotDeleteNewRedisLease() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        RunOwnerLease oldLease = lease(4L);
        RunOwnerLease newLease = lease(5L);
        when(store.renewOwnerLease(oldLease)).thenReturn(Optional.of(oldLease));
        when(store.renewOwnerLease(newLease)).thenReturn(Optional.of(newLease));
        RunOwnerLeaseSupervisor supervisor = supervisor(store);
        RunOwnerLeaseSupervisor.OwnershipHandle oldHandle = supervisor.adopt(oldLease).orElseThrow();
        RunOwnerLeaseSupervisor.OwnershipHandle newHandle = supervisor.adopt(newLease).orElseThrow();

        supervisor.release(oldHandle);

        verify(store, never()).releaseOwnerLease(oldLease);
        assertThat(supervisor.isOwned(RUN_ID)).isTrue();
        supervisor.release(newHandle);
        verify(store).releaseOwnerLease(newLease);
    }

    private RunOwnerLeaseSupervisor supervisor(RunRuntimeStore store) {
        return new RunOwnerLeaseSupervisor(store, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RunOwnerLease lease(long token) {
        return new RunOwnerLease(RUN_ID, "bjp_owner", token, NOW.plusSeconds(15));
    }

    private AtomicBoolean lostSignal(RunOwnerLeaseSupervisor.OwnershipHandle handle) {
        AtomicBoolean lost = new AtomicBoolean();
        handle.lost().doOnSuccess(ignored -> lost.set(true)).subscribe();
        return lost;
    }
}
