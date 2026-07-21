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
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.run.ScheduledRunMetadata;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证 owner 本机只在 handle 消失后收敛过期 attempt，并始终先查 Run 锚点。 */
class NightExecutionDispatchOwnerWatchdogTest {

    @Test
    void repairsAcceptedRunBeforeRetryingExpiredOwnedAttempt() {
        Instant now = Instant.parse("2026-07-18T13:20:00Z");
        NightExecutionTask task = dispatchingTask(now);
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        when(repository.findDispatchingByOwner("bjp_owner", 100)).thenReturn(List.of(task));
        NightExecutionRunLifecycleService lifecycle = mock(NightExecutionRunLifecycleService.class);
        Run run = new Run(new RunId("run_owner_watchdog"), task.sessionId(), task.workspaceId(),
                RunStatus.RUNNING, now, now, "trace_owner_watchdog");
        when(lifecycle.findAcceptedRun(task)).thenReturn(Optional.of(run));
        NightExecutionDispatchLeaseGuard guard = mock(NightExecutionDispatchLeaseGuard.class);
        when(guard.isInFlight(task.taskId(), task.dispatchAttemptId())).thenReturn(false);

        watchdog(repository, lifecycle, guard, now).reconcileOwnedAttempts();

        verify(lifecycle).onAccepted(
                new ScheduledRunMetadata(task.taskId().value(), task.dispatchAttemptId()), run);
        verify(repository, never()).updateDispatchIfAttempt(any(), any());
    }

    @Test
    void retriesExpiredOwnedAttemptOnlyAfterInFlightHandleDisappears() {
        Instant now = Instant.parse("2026-07-18T13:20:00Z");
        NightExecutionTask task = dispatchingTask(now);
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        when(repository.findDispatchingByOwner("bjp_owner", 100)).thenReturn(List.of(task));
        when(repository.updateDispatchIfAttempt(any(), eq(task.dispatchAttemptId()))).thenReturn(true);
        NightExecutionRunLifecycleService lifecycle = mock(NightExecutionRunLifecycleService.class);
        when(lifecycle.findAcceptedRun(task)).thenReturn(Optional.empty());
        NightExecutionDispatchLeaseGuard guard = mock(NightExecutionDispatchLeaseGuard.class);
        when(guard.isInFlight(task.taskId(), task.dispatchAttemptId())).thenReturn(false);

        watchdog(repository, lifecycle, guard, now).reconcileOwnedAttempts();

        ArgumentCaptor<NightExecutionTask> retried = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(repository).updateDispatchIfAttempt(retried.capture(), eq(task.dispatchAttemptId()));
        assertThat(retried.getValue().status()).isEqualTo(NightExecutionTaskStatus.SCHEDULED);
    }

    private NightExecutionDispatchOwnerWatchdog watchdog(
            NightExecutionTaskRepository repository,
            NightExecutionRunLifecycleService lifecycle,
            NightExecutionDispatchLeaseGuard guard,
            Instant now) {
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        when(identity.backendProcessId()).thenReturn("bjp_owner");
        return new NightExecutionDispatchOwnerWatchdog(
                repository, lifecycle, guard, identity, Clock.fixed(now, ZoneOffset.UTC));
    }

    private NightExecutionTask dispatchingTask(Instant now) {
        Instant created = Instant.parse("2026-07-18T12:00:00Z");
        return new NightExecutionTask(
                new NightExecutionTaskId("net_owner_watchdog"), new UserId("usr_owner_watchdog"),
                new SessionId("ses_owner_watchdog"), new WorkspaceId("wrk_owner_watchdog"),
                "request-owner-watchdog", "夜间执行", "执行任务", "{\"prompt\":\"执行任务\"}",
                NightExecutionTaskStatus.SCHEDULED, Instant.parse("2026-07-18T13:00:00Z"),
                Instant.parse("2026-07-18T13:15:00Z"), Instant.parse("2026-07-18T23:00:00Z"),
                "linux-night-a", null, null, 0, false, null, null, null, null, null,
                "trace_owner_watchdog", created, created)
                .startDispatch("nda_owner", "bjp_owner", now.minusSeconds(60), now.minusSeconds(360));
    }
}
