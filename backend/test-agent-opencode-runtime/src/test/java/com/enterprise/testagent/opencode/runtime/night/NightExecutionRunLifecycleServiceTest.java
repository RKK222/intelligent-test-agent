package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunPersistenceAnchor;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunSummaryPersistencePort;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.run.ScheduledRunMetadata;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证普通 Run 受理边界负责释放夜间锁和容量，Run 终态不再反向修改。 */
class NightExecutionRunLifecycleServiceTest {

    @Test
    void acceptedRunMarksSameAttemptDispatchedAndReleasesReservation() {
        Instant now = Instant.parse("2026-07-18T13:02:00Z");
        NightExecutionTask task = dispatchingTask();
        NightExecutionTaskRepository tasks = mock(NightExecutionTaskRepository.class);
        when(tasks.findById(task.taskId())).thenReturn(Optional.of(task));
        when(tasks.updateDispatchIfAttempt(any(), eq("nda_lifecycle"))).thenReturn(true);
        NightExecutionRunLifecycleService service = new NightExecutionRunLifecycleService(
                tasks, mock(SessionMessageRepository.class), mock(RunSummaryPersistencePort.class),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(now, ZoneOffset.UTC));
        Run run = new Run(new RunId("run_lifecycle"), task.sessionId(), task.workspaceId(),
                RunStatus.RUNNING, now, now, "trace_lifecycle");

        service.onAccepted(new ScheduledRunMetadata(task.taskId().value(), "nda_lifecycle"), run);

        ArgumentCaptor<NightExecutionTask> updated = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(tasks).updateDispatchIfAttempt(updated.capture(), eq("nda_lifecycle"));
        assertThat(updated.getValue().status()).isEqualTo(NightExecutionTaskStatus.DISPATCHED);
        assertThat(updated.getValue().runInputJson()).isNull();
        assertThat(updated.getValue().runId()).isEqualTo(run.runId());
        verify(tasks).deleteSessionLock(task.sessionId(), task.taskId());
        verify(tasks).releaseSlot(task.slotStart(), now);
    }

    @Test
    void findsRedisAnchorByStableRunClientRequestIdFromSnapshot() {
        Instant now = Instant.parse("2026-07-18T13:02:00Z");
        NightExecutionTask task = dispatchingTask();
        RunSummaryPersistencePort summaries = mock(RunSummaryPersistencePort.class);
        RunPersistenceAnchor anchor = new RunPersistenceAnchor(
                new RunId("run_lifecycle_anchor"), task.sessionId(), task.workspaceId(),
                RunStatus.SUCCEEDED, RunStorageMode.REDIS_SUMMARY, 1L,
                "run-request-lifecycle", "linux-night-a", "node-night-a", "opc-night-a",
                null, "msg-lifecycle", null, null, null,
                new SessionMessageId("msg_lifecycle_summary"),
                "trace_lifecycle", now, now, now.plusSeconds(3600),
                ConversationSourceType.SCHEDULED_TASK, task.taskId().value(), task.ownerUserId(),
                "build", null);
        when(summaries.findBySessionAndClientRequestId(
                task.sessionId(), "run-request-lifecycle")).thenReturn(Optional.of(anchor));
        NightExecutionRunLifecycleService service = new NightExecutionRunLifecycleService(
                mock(NightExecutionTaskRepository.class), mock(SessionMessageRepository.class), summaries,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(now, ZoneOffset.UTC));

        Optional<Run> found = service.findAcceptedRun(task);

        assertThat(found).map(Run::runId).contains(anchor.runId());
        verify(summaries).findBySessionAndClientRequestId(task.sessionId(), "run-request-lifecycle");
    }

    @Test
    void acceptsOnlyFinalizedLegacyAnchorOwnedByTheSameScheduledTask() {
        Instant now = Instant.parse("2026-07-18T13:02:00Z");
        NightExecutionTask task = dispatchingTask();
        RunSummaryPersistencePort summaries = mock(RunSummaryPersistencePort.class);
        RunPersistenceAnchor foreign = new RunPersistenceAnchor(
                new RunId("run_lifecycle_legacy"), task.sessionId(), task.workspaceId(),
                RunStatus.PENDING, RunStorageMode.LEGACY_FULL, 0L,
                "run-request-lifecycle", "linux-night-a", null, null,
                null, "msg-lifecycle", "nda_lifecycle", now.plusSeconds(300), null,
                null, "trace_lifecycle", now, now, null,
                ConversationSourceType.SCHEDULED_TASK, "net_another_task", task.ownerUserId(),
                "build", null);
        when(summaries.findBySessionAndClientRequestId(
                task.sessionId(), "run-request-lifecycle")).thenReturn(Optional.of(foreign));
        SessionMessageRepository messages = mock(SessionMessageRepository.class);
        NightExecutionRunLifecycleService service = new NightExecutionRunLifecycleService(
                mock(NightExecutionTaskRepository.class), messages, summaries,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.findAcceptedRun(task)).isEmpty();

        RunPersistenceAnchor owned = new RunPersistenceAnchor(
                foreign.runId(), foreign.sessionId(), foreign.workspaceId(), foreign.status(),
                foreign.storageMode(), foreign.statusVersion(), foreign.clientRequestId(),
                foreign.producerLinuxServerId(), foreign.executionNodeIdSnapshot(),
                foreign.opencodeProcessIdSnapshot(), foreign.rootRemoteSessionId(),
                foreign.dispatchMessageId(), foreign.scheduledDispatchAttemptId(),
                foreign.scheduledDispatchLeaseUntil(), null,
                foreign.assistantSummaryMessageId(), foreign.traceId(),
                foreign.createdAt(), foreign.updatedAt(), foreign.detailsExpiresAt(), foreign.sourceType(),
                task.taskId().value(), foreign.triggeredByUserId(), foreign.agentId(), foreign.modelId());
        when(summaries.findBySessionAndClientRequestId(
                task.sessionId(), "run-request-lifecycle")).thenReturn(Optional.of(owned));
        when(messages.findBySessionIdAndRemoteMessageId(task.sessionId(), "msg-lifecycle"))
                .thenReturn(Optional.of(new SessionMessage(
                        new SessionMessageId("msg_platform_lifecycle"), task.sessionId(),
                        SessionMessageRole.USER, "执行任务", now, "trace_lifecycle",
                        owned.runId(), "opencode", "msg-lifecycle", null, null, null, now,
                        ConversationSourceType.SCHEDULED_TASK, task.taskId().value(), task.ownerUserId())));
        assertThat(service.findAcceptedRun(task)).isEmpty();

        RunPersistenceAnchor accepted = new RunPersistenceAnchor(
                owned.runId(), owned.sessionId(), owned.workspaceId(), owned.status(),
                owned.storageMode(), owned.statusVersion(), owned.clientRequestId(),
                owned.producerLinuxServerId(), owned.executionNodeIdSnapshot(),
                owned.opencodeProcessIdSnapshot(), owned.rootRemoteSessionId(),
                owned.dispatchMessageId(), owned.scheduledDispatchAttemptId(),
                owned.scheduledDispatchLeaseUntil(), now,
                owned.assistantSummaryMessageId(), owned.traceId(), owned.createdAt(), owned.updatedAt(),
                owned.detailsExpiresAt(), owned.sourceType(), owned.sourceRefId(), owned.triggeredByUserId(),
                owned.agentId(), owned.modelId());
        when(summaries.findBySessionAndClientRequestId(
                task.sessionId(), "run-request-lifecycle")).thenReturn(Optional.of(accepted));
        assertThat(service.findAcceptedRun(task)).map(Run::runId).contains(accepted.runId());
    }

    private NightExecutionTask dispatchingTask() {
        Instant created = Instant.parse("2026-07-18T12:00:00Z");
        Instant claimed = Instant.parse("2026-07-18T13:01:00Z");
        return new NightExecutionTask(
                new NightExecutionTaskId("net_lifecycle"), new UserId("usr_lifecycle"),
                new SessionId("ses_lifecycle"), new WorkspaceId("wrk_lifecycle"),
                "request-lifecycle", "夜间执行", "执行任务",
                "{\"prompt\":\"执行任务\",\"messageId\":\"msg-lifecycle\","
                        + "\"clientRequestId\":\"run-request-lifecycle\"}",
                NightExecutionTaskStatus.SCHEDULED, Instant.parse("2026-07-18T13:00:00Z"),
                Instant.parse("2026-07-18T13:15:00Z"), Instant.parse("2026-07-18T23:00:00Z"),
                "linux-night-a", null, null, 0, false, null, null, null, null, null,
                "trace_lifecycle", created, created)
                .startDispatch("nda_lifecycle", "bjp_lifecycle", claimed.plusSeconds(300), claimed);
    }
}
