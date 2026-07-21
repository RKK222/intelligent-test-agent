package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.run.ScheduledRunMetadata;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证补偿先查 Run 锚点，再按租约和精确 backendProcessId 心跳决定是否恢复。 */
class NightExecutionReconcileServiceTest {

    @Test
    void repairsDispatchedStateFromExistingRunBeforeConsideringLeaseOwner() {
        Instant now = Instant.parse("2026-07-18T13:20:00Z");
        NightExecutionTask task = dispatchingTask(now.minusSeconds(600), now.minusSeconds(60));
        NightExecutionTaskRepository repository = baseRepository(now, task);
        NightExecutionRunLifecycleService lifecycle = mock(NightExecutionRunLifecycleService.class);
        Run run = new Run(new RunId("run_recovered"), task.sessionId(), task.workspaceId(),
                RunStatus.RUNNING, now, now, "trace_reconcile");
        when(lifecycle.findAcceptedRun(task)).thenReturn(Optional.of(run));
        NightExecutionReconcileService service = service(repository, lifecycle, now);

        NightExecutionReconcileService.Result result = service.reconcile("trace_reconcile", () -> false);

        assertThat(result.repaired()).isEqualTo(1);
        verify(lifecycle).onAccepted(
                new ScheduledRunMetadata(task.taskId().value(), task.dispatchAttemptId()), run);
        verify(repository, never()).updateDispatchIfAttempt(any(), any());
    }

    @Test
    void keepsExpiredLeaseWhenRemoteOwnerHeartbeatIsStillLive() {
        Instant now = Instant.parse("2026-07-18T13:20:00Z");
        NightExecutionTask task = dispatchingTask(now.minusSeconds(600), now.minusSeconds(60));
        NightExecutionTaskRepository repository = baseRepository(now, task);
        NightExecutionRunLifecycleService lifecycle = mock(NightExecutionRunLifecycleService.class);
        when(lifecycle.findAcceptedRun(task)).thenReturn(Optional.empty());
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        BackendProcessId owner = new BackendProcessId(task.dispatchOwnerBackendProcessId());
        when(routes.isCurrent(owner)).thenReturn(false);
        when(routes.requireBackend(owner)).thenReturn(new BackendJavaProcess(
                owner, new LinuxServerId("linux-night-a"), "http://10.0.0.2:8080",
                BackendJavaProcessStatus.READY, now, now, now, now, "trace_reconcile"));
        NightExecutionReconcileService service = new NightExecutionReconcileService(
                repository, lifecycle, routes, mock(NightExecutionDispatchLeaseGuard.class),
                Clock.fixed(now, ZoneOffset.UTC));

        NightExecutionReconcileService.Result result = service.reconcile("trace_reconcile", () -> false);

        assertThat(result.heartbeatSkipped()).isEqualTo(1);
        verify(repository, never()).updateDispatchIfAttempt(any(), any());
    }

    @Test
    void windowEndDoesNotFailAnAttemptStillProtectedByLocalInFlightGuard() {
        Instant now = Instant.parse("2026-07-18T23:00:00Z");
        NightExecutionTask task = scheduledTask(now)
                .startDispatch("nda_reconcile", "bjp_local_owner", now.minusSeconds(60), now.minusSeconds(600));
        NightExecutionTaskRepository repository = baseRepository(now, task);
        NightExecutionRunLifecycleService lifecycle = mock(NightExecutionRunLifecycleService.class);
        when(lifecycle.findAcceptedRun(task)).thenReturn(Optional.empty());
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        BackendProcessId owner = new BackendProcessId(task.dispatchOwnerBackendProcessId());
        when(routes.isCurrent(owner)).thenReturn(true);
        NightExecutionDispatchLeaseGuard guard = mock(NightExecutionDispatchLeaseGuard.class);
        when(guard.isInFlight(task.taskId(), task.dispatchAttemptId())).thenReturn(true);
        NightExecutionReconcileService service = new NightExecutionReconcileService(
                repository, lifecycle, routes, guard, Clock.fixed(now, ZoneOffset.UTC));

        NightExecutionReconcileService.Result result = service.reconcile("trace_reconcile", () -> false);

        assertThat(result.heartbeatSkipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(repository, never()).updateDispatchIfAttempt(any(), any());
    }

    @Test
    void retriesSameAttemptWhenRemoteOwnerHeartbeatDisappears() {
        Instant now = Instant.parse("2026-07-18T13:20:00Z");
        NightExecutionTask task = dispatchingTask(now.minusSeconds(600), now.minusSeconds(60));
        NightExecutionTaskRepository repository = baseRepository(now, task);
        when(repository.updateDispatchIfAttempt(any(), eq(task.dispatchAttemptId()))).thenReturn(true);
        NightExecutionRunLifecycleService lifecycle = mock(NightExecutionRunLifecycleService.class);
        when(lifecycle.findAcceptedRun(task)).thenReturn(Optional.empty());
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        BackendProcessId owner = new BackendProcessId(task.dispatchOwnerBackendProcessId());
        when(routes.isCurrent(owner)).thenReturn(false);
        when(routes.requireBackend(owner)).thenThrow(new IllegalStateException("owner offline"));
        NightExecutionReconcileService service = new NightExecutionReconcileService(
                repository, lifecycle, routes, mock(NightExecutionDispatchLeaseGuard.class),
                Clock.fixed(now, ZoneOffset.UTC));

        NightExecutionReconcileService.Result result = service.reconcile("trace_reconcile", () -> false);

        assertThat(result.retried()).isEqualTo(1);
        ArgumentCaptor<NightExecutionTask> retried = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(repository).updateDispatchIfAttempt(retried.capture(), eq(task.dispatchAttemptId()));
        assertThat(retried.getValue().status()).isEqualTo(NightExecutionTaskStatus.SCHEDULED);
        assertThat(retried.getValue().dispatchAttemptId()).isNull();
    }

    @Test
    void failsScheduledTaskAtWindowEndAndReleasesLockAndCapacity() {
        Instant now = Instant.parse("2026-07-18T23:00:00Z");
        NightExecutionTask task = scheduledTask(now);
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        when(repository.findDispatchingLeaseExpiredBefore(now, 50)).thenReturn(List.of());
        when(repository.findScheduledWindowExpired(now, 50)).thenReturn(List.of(task));
        when(repository.findTerminalBefore(now.minusSeconds(30L * 24 * 3600), 50)).thenReturn(List.of());
        when(repository.updateIfStatus(any(), eq(NightExecutionTaskStatus.SCHEDULED))).thenReturn(true);
        NightExecutionReconcileService service = service(
                repository, mock(NightExecutionRunLifecycleService.class), now);

        NightExecutionReconcileService.Result result = service.reconcile("trace_reconcile", () -> false);

        assertThat(result.failed()).isEqualTo(1);
        ArgumentCaptor<NightExecutionTask> failed = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(repository).updateIfStatus(failed.capture(), eq(NightExecutionTaskStatus.SCHEDULED));
        assertThat(failed.getValue().status()).isEqualTo(NightExecutionTaskStatus.FAILED);
        verify(repository).deleteSessionLock(task.sessionId(), task.taskId());
        verify(repository).releaseSlot(task.slotStart(), now);
    }

    private NightExecutionTaskRepository baseRepository(Instant now, NightExecutionTask task) {
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        when(repository.findDispatchingLeaseExpiredBefore(now, 50)).thenReturn(List.of(task));
        when(repository.findScheduledWindowExpired(now, 50)).thenReturn(List.of());
        when(repository.findTerminalBefore(now.minusSeconds(30L * 24 * 3600), 50)).thenReturn(List.of());
        return repository;
    }

    private NightExecutionReconcileService service(
            NightExecutionTaskRepository repository,
            NightExecutionRunLifecycleService lifecycle,
            Instant now) {
        return new NightExecutionReconcileService(
                repository, lifecycle, mock(BackendJavaRouteResolver.class),
                mock(NightExecutionDispatchLeaseGuard.class), Clock.fixed(now, ZoneOffset.UTC));
    }

    private NightExecutionTask dispatchingTask(Instant claimedAt, Instant leaseUntil) {
        return scheduledTask(Instant.parse("2026-07-18T23:00:00Z"))
                .startDispatch("nda_reconcile", "bjp_remote_owner", leaseUntil, claimedAt);
    }

    private NightExecutionTask scheduledTask(Instant windowEnd) {
        Instant created = Instant.parse("2026-07-18T12:00:00Z");
        return new NightExecutionTask(
                new NightExecutionTaskId("net_night_reconcile"), new UserId("usr_night_reconcile"),
                new SessionId("ses_night_reconcile"), new WorkspaceId("wrk_night_reconcile"),
                "request-night-reconcile", "夜间回归", "执行回归", "{}",
                NightExecutionTaskStatus.SCHEDULED,
                Instant.parse("2026-07-18T13:00:00Z"), Instant.parse("2026-07-18T13:15:00Z"),
                windowEnd, "linux-night-a", null, null, 0, false,
                null, null, null, null, null, "trace_reconcile", created, created);
    }
}
