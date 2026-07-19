package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.process.OpencodeScheduledTaskExecutionAffinityProvider;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.scheduler.ScheduledUserPlanService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证错过时段的任务在同一夜间窗口内顺延，并原子转移容量占位。 */
class NightExecutionReconcileServiceTest {

    @Test
    void rollsStaleDispatchToNextAvailableQuarter() {
        Instant now = Instant.parse("2026-07-18T13:20:00Z");
        UserId owner = new UserId("usr_night_reconcile");
        NightExecutionTask task = new NightExecutionTask(
                new NightExecutionTaskId("net_night_reconcile"), owner,
                new SessionId("ses_night_reconcile"), new WorkspaceId("wrk_night_reconcile"),
                "request-night-reconcile", "夜间回归", "执行回归", "{}",
                NightExecutionTaskStatus.DISPATCHING,
                Instant.parse("2026-07-18T13:00:00Z"), Instant.parse("2026-07-18T13:15:00Z"),
                Instant.parse("2026-07-18T23:00:00Z"), "linux-night-a",
                new ScheduledTaskRunId("str_night_reconcile_old"), null, 0, false,
                Instant.parse("2026-07-18T13:01:00Z"), null, null, null, null,
                "trace_night_reconcile", Instant.parse("2026-07-18T12:00:00Z"),
                Instant.parse("2026-07-18T13:01:00Z"));

        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        ScheduledUserPlanService userPlans = mock(ScheduledUserPlanService.class);
        UserOpencodeProcessAssignmentService assignment = mock(UserOpencodeProcessAssignmentService.class);
        OpencodeScheduledTaskExecutionAffinityProvider affinity = mock(OpencodeScheduledTaskExecutionAffinityProvider.class);
        NightExecutionProperties properties = new NightExecutionProperties();
        properties.setSlotCapacity("2");
        when(repository.findDispatchingBefore(now.minusSeconds(300), 50)).thenReturn(List.of(task));
        when(repository.findScheduledDueBefore(now.minusSeconds(300), 50)).thenReturn(List.of());
        when(repository.findTerminalBefore(now.minusSeconds(30L * 24 * 3600), 50)).thenReturn(List.of());
        when(repository.reservationCounts(any(), any())).thenReturn(Map.of());
        when(repository.reserveSlot(Instant.parse("2026-07-18T13:30:00Z"), 2, now)).thenReturn(true);
        when(repository.updateIfStatus(any(), eq(NightExecutionTaskStatus.DISPATCHING))).thenReturn(true);
        when(assignment.routingLinuxServerId(owner, "opencode")).thenReturn(Optional.of("linux-night-a"));
        when(affinity.currentAffinity()).thenReturn("linux-current");
        when(userPlans.schedule(any(), eq(owner), eq(Instant.parse("2026-07-18T13:30:00Z")),
                eq("linux-night-a"), any())).thenReturn(ScheduledTaskRun.pending(
                        new ScheduledTaskRunId("str_night_reconcile_new"),
                        new ScheduledTaskKey("opencode-runtime.night-execution"), null,
                        ScheduledTaskTriggerType.USER_PLAN, owner,
                        Instant.parse("2026-07-18T13:30:00Z"), "linux-night-a",
                        "trace_night_reconcile", now));

        NightExecutionReconcileService service = new NightExecutionReconcileService(
                repository, userPlans, assignment, affinity, properties,
                Clock.fixed(now, ZoneOffset.UTC));
        NightExecutionReconcileService.Result result = service.reconcile("trace_night_reconcile", () -> false);

        assertThat(result.rolledOver()).isEqualTo(1);
        ArgumentCaptor<NightExecutionTask> updated = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(repository).updateIfStatus(updated.capture(), eq(NightExecutionTaskStatus.DISPATCHING));
        assertThat(updated.getValue().slotStart()).isEqualTo(Instant.parse("2026-07-18T13:30:00Z"));
        assertThat(updated.getValue().rolloverCount()).isEqualTo(1);
        verify(userPlans).cancelPendingIfPresent(task.scheduledTaskRunId(), "夜间任务原执行计划已失效");
        verify(repository).releaseSlot(task.slotStart(), now);
        verify(repository).deleteReservationsBefore(now.minusSeconds(30L * 24 * 3600));
    }

    @Test
    void rejectsOrphanReplacementWhenTaskStateChangesDuringRecovery() {
        Instant now = Instant.parse("2026-07-18T13:06:00Z");
        UserId owner = new UserId("usr_night_reconcile_race");
        NightExecutionTask task = new NightExecutionTask(
                new NightExecutionTaskId("net_night_reconcile_race"), owner,
                new SessionId("ses_night_reconcile_race"), new WorkspaceId("wrk_night_reconcile_race"),
                "request-night-reconcile-race", "夜间回归", "执行回归", "{}",
                NightExecutionTaskStatus.SCHEDULED,
                Instant.parse("2026-07-18T13:00:00Z"), Instant.parse("2026-07-18T13:15:00Z"),
                Instant.parse("2026-07-18T23:00:00Z"), "linux-night-a",
                new ScheduledTaskRunId("str_night_reconcile_race_old"), null, 0, false,
                null, null, null, null, null, "trace_night_reconcile_race",
                Instant.parse("2026-07-18T12:00:00Z"), Instant.parse("2026-07-18T12:00:00Z"));
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        ScheduledUserPlanService userPlans = mock(ScheduledUserPlanService.class);
        UserOpencodeProcessAssignmentService assignment = mock(UserOpencodeProcessAssignmentService.class);
        OpencodeScheduledTaskExecutionAffinityProvider affinity = mock(OpencodeScheduledTaskExecutionAffinityProvider.class);
        NightExecutionProperties properties = new NightExecutionProperties();
        properties.setSlotCapacity("2");
        when(repository.findDispatchingBefore(now.minusSeconds(300), 50)).thenReturn(List.of());
        when(repository.findScheduledDueBefore(now.minusSeconds(300), 50)).thenReturn(List.of(task));
        when(repository.updateIfStatus(any(), eq(NightExecutionTaskStatus.SCHEDULED))).thenReturn(false);
        when(assignment.routingLinuxServerId(owner, "opencode")).thenReturn(Optional.of("linux-night-a"));
        when(userPlans.schedule(any(), eq(owner), eq(now), eq("linux-night-a"), any()))
                .thenReturn(ScheduledTaskRun.pending(
                        new ScheduledTaskRunId("str_night_reconcile_race_new"),
                        new ScheduledTaskKey("opencode-runtime.night-execution"), null,
                        ScheduledTaskTriggerType.USER_PLAN, owner, now, "linux-night-a",
                        "trace_night_reconcile_race", now));

        NightExecutionReconcileService service = new NightExecutionReconcileService(
                repository, userPlans, assignment, affinity, properties,
                Clock.fixed(now, ZoneOffset.UTC));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.reconcile("trace_night_reconcile_race", () -> false))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    void failsScheduledTaskAtSevenAndReleasesItsSessionAndCapacity() {
        Instant now = Instant.parse("2026-07-18T23:00:00Z");
        UserId owner = new UserId("usr_night_reconcile_expired");
        NightExecutionTask task = new NightExecutionTask(
                new NightExecutionTaskId("net_night_reconcile_expired"), owner,
                new SessionId("ses_night_reconcile_expired"), new WorkspaceId("wrk_night_reconcile_expired"),
                "request-night-reconcile-expired", "夜间回归", "执行回归", "{}",
                NightExecutionTaskStatus.SCHEDULED,
                Instant.parse("2026-07-18T22:45:00Z"), Instant.parse("2026-07-18T23:00:00Z"),
                now, "linux-night-a", new ScheduledTaskRunId("str_night_reconcile_expired"),
                null, 0, false, null, null, null, null, null,
                "trace_night_reconcile_expired", Instant.parse("2026-07-18T12:00:00Z"),
                Instant.parse("2026-07-18T22:45:00Z"));
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        ScheduledUserPlanService userPlans = mock(ScheduledUserPlanService.class);
        UserOpencodeProcessAssignmentService assignment = mock(UserOpencodeProcessAssignmentService.class);
        OpencodeScheduledTaskExecutionAffinityProvider affinity = mock(OpencodeScheduledTaskExecutionAffinityProvider.class);
        NightExecutionProperties properties = new NightExecutionProperties();
        properties.setSlotCapacity("2");
        when(repository.findDispatchingBefore(now.minusSeconds(300), 50)).thenReturn(List.of());
        when(repository.findScheduledDueBefore(now.minusSeconds(300), 50)).thenReturn(List.of(task));
        when(repository.findTerminalBefore(now.minusSeconds(30L * 24 * 3600), 50)).thenReturn(List.of());
        when(repository.updateIfStatus(any(), eq(NightExecutionTaskStatus.SCHEDULED))).thenReturn(true);

        NightExecutionReconcileService service = new NightExecutionReconcileService(
                repository, userPlans, assignment, affinity, properties,
                Clock.fixed(now, ZoneOffset.UTC));
        NightExecutionReconcileService.Result result = service.reconcile(
                "trace_night_reconcile_expired", () -> false);

        assertThat(result.failed()).isEqualTo(1);
        ArgumentCaptor<NightExecutionTask> failed = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(repository).updateIfStatus(failed.capture(), eq(NightExecutionTaskStatus.SCHEDULED));
        assertThat(failed.getValue().status()).isEqualTo(NightExecutionTaskStatus.FAILED);
        assertThat(failed.getValue().errorCode()).isEqualTo("WINDOW_EXPIRED");
        verify(userPlans).cancelPendingIfPresent(task.scheduledTaskRunId(), "夜间任务原执行计划已失效");
        verify(repository).deleteSessionLock(task.sessionId(), task.taskId());
        verify(repository).releaseSlot(task.slotStart(), now);
    }
}
