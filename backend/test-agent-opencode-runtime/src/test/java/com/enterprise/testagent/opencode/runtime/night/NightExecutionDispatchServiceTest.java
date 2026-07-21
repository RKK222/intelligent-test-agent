package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.enterprise.testagent.opencode.runtime.run.ConversationContextApplicationService;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.run.ScheduledRunMetadata;
import com.enterprise.testagent.opencode.runtime.run.StartRunInput;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证目标 Java 只认领固定服务器任务，并复用普通 Scheduled Run 启动链路。 */
class NightExecutionDispatchServiceTest {

    @Test
    void claimsWithLeaseAndReturnsWhenOrdinaryRunIsAccepted() throws Exception {
        Instant now = Instant.parse("2026-07-18T13:01:00Z");
        UserId owner = new UserId("usr_night_dispatch");
        SessionId sessionId = new SessionId("ses_night_dispatch");
        WorkspaceId workspaceId = new WorkspaceId("wrk_night_dispatch");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        NightExecutionRunInputSnapshot snapshot = new NightExecutionRunInputSnapshot(
                "执行夜间回归", List.of(StartRunInput.PromptPart.text("执行夜间回归")),
                "msg-night-dispatch", "build", null, null, "build", null, null,
                "run-request-night-dispatch");
        NightExecutionTask task = new NightExecutionTask(
                new NightExecutionTaskId("net_night_dispatch"), owner, sessionId, workspaceId,
                "request-night-dispatch", "夜间回归", "执行夜间回归",
                objectMapper.writeValueAsString(snapshot), NightExecutionTaskStatus.SCHEDULED,
                Instant.parse("2026-07-18T13:00:00Z"), Instant.parse("2026-07-18T13:15:00Z"),
                Instant.parse("2026-07-18T23:00:00Z"), "linux-night-a", null,
                null, 0, false, null, null, null, null, null, "trace_night_dispatch",
                Instant.parse("2026-07-18T12:00:00Z"), Instant.parse("2026-07-18T12:00:00Z"));

        NightExecutionTaskRepository tasks = mock(NightExecutionTaskRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        ConversationWorkspaceAccessAuthorizer access = mock(ConversationWorkspaceAccessAuthorizer.class);
        UserOpencodeProcessAssignmentService assignment = mock(UserOpencodeProcessAssignmentService.class);
        ConversationContextApplicationService contexts = mock(ConversationContextApplicationService.class);
        RunApplicationService runs = mock(RunApplicationService.class);
        NightExecutionRunLifecycleService lifecycle = mock(NightExecutionRunLifecycleService.class);
        NightExecutionDispatchLeaseGuard leases = mock(NightExecutionDispatchLeaseGuard.class);
        NightExecutionDispatchLeaseGuard.Handle lease = mock(NightExecutionDispatchLeaseGuard.Handle.class);
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);

        when(identity.linuxServerId()).thenReturn("linux-night-a");
        when(identity.backendProcessId()).thenReturn("bjp_night_a");
        when(tasks.findById(task.taskId())).thenReturn(Optional.of(task));
        when(tasks.claimForDispatch(any(), eq("linux-night-a"))).thenReturn(true);
        when(tasks.renewDispatchLease(eq(task.taskId()), eq("nda_fixed_attempt"), any(), eq(now)))
                .thenReturn(true);
        when(leases.track(task.taskId(), "nda_fixed_attempt")).thenReturn(lease);
        when(lifecycle.findAcceptedRun(any())).thenReturn(Optional.empty());
        when(sessions.findById(sessionId)).thenReturn(Optional.of(new Session(
                sessionId, workspaceId, "夜间回归", SessionStatus.ACTIVE, now, now,
                "trace_night_dispatch", null, null, false, ConversationSourceType.MANUAL, null, owner)));
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(new Workspace(
                workspaceId, "night", "/tmp/night", WorkspaceStatus.ACTIVE, now, now,
                "linux-night-a", "trace_night_dispatch")));
        when(assignment.routingLinuxServerId(owner, "opencode")).thenReturn(Optional.of("linux-night-a"));
        when(assignment.initialize(owner, "opencode", "trace_night_dispatch"))
                .thenReturn(new UserOpencodeProcessStatusResponse(
                        UserOpencodeProcessAvailability.READY, false, "ready", "ocp_night_dispatch",
                        "linux-night-a", "container-night", 4096, "http://127.0.0.1:4096", now));
        ConversationContextApplicationService.IssuedConversationContext issued =
                mock(ConversationContextApplicationService.IssuedConversationContext.class);
        when(issued.contextToken()).thenReturn("ctx_night_dispatch");
        when(contexts.bootstrap(owner, "opencode", sessionId, "trace_night_dispatch")).thenReturn(issued);
        Run run = new Run(new RunId("run_night_dispatch"), sessionId, workspaceId,
                RunStatus.RUNNING, now, now, "trace_night_dispatch")
                .withSource(ConversationSourceType.SCHEDULED_TASK, task.taskId().value(), owner);
        when(runs.startScheduledRun(eq(owner), any(), any(ScheduledRunMetadata.class),
                eq("trace_night_dispatch"))).thenReturn(run);

        NightExecutionDispatchService service = new NightExecutionDispatchService(
                tasks, sessions, workspaces, access, assignment, contexts, runs, lifecycle, leases,
                identity, objectMapper, Clock.fixed(now, ZoneOffset.UTC), () -> "nda_fixed_attempt");
        NightExecutionDispatchBatchResult batch = service.dispatchBatch(
                "linux-night-a", List.of(task.taskId()), "trace_night_dispatch").block();

        assertThat(batch.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(NightExecutionDispatchStatus.STARTED);
            assertThat(result.runId()).isEqualTo("run_night_dispatch");
        });
        ArgumentCaptor<NightExecutionTask> claimed = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(tasks).claimForDispatch(claimed.capture(), eq("linux-night-a"));
        assertThat(claimed.getValue().dispatchAttemptId()).isEqualTo("nda_fixed_attempt");
        assertThat(claimed.getValue().dispatchOwnerBackendProcessId()).isEqualTo("bjp_night_a");
        assertThat(claimed.getValue().dispatchLeaseUntil()).isEqualTo(now.plusSeconds(300));
        verify(runs).startScheduledRun(eq(owner), any(),
                eq(new ScheduledRunMetadata(task.taskId().value(), "nda_fixed_attempt")),
                eq("trace_night_dispatch"));
        verify(tasks).renewDispatchLease(
                task.taskId(), "nda_fixed_attempt", now.plusSeconds(300), now);
        verify(lease).close();
    }

    @Test
    void responseLossRetryFindsExistingRunWithoutStartingAnotherOne() {
        Instant now = Instant.parse("2026-07-18T13:02:00Z");
        UserId owner = new UserId("usr_night_retry");
        SessionId sessionId = new SessionId("ses_night_retry");
        WorkspaceId workspaceId = new WorkspaceId("wrk_night_retry");
        NightExecutionTask scheduled = new NightExecutionTask(
                new NightExecutionTaskId("net_night_retry"), owner, sessionId, workspaceId,
                "request-night-retry", "夜间重试", "执行夜间重试", "{}",
                NightExecutionTaskStatus.SCHEDULED,
                Instant.parse("2026-07-18T13:00:00Z"), Instant.parse("2026-07-18T13:15:00Z"),
                Instant.parse("2026-07-18T23:00:00Z"), "linux-night-a", null,
                null, 0, false, null, null, null, null, null, "trace_night_retry",
                Instant.parse("2026-07-18T12:00:00Z"), Instant.parse("2026-07-18T12:00:00Z"));
        NightExecutionTask dispatching = scheduled.startDispatch(
                "nda_existing_attempt", "bjp_night_a", now.plusSeconds(300), now.minusSeconds(30));
        Run existingRun = new Run(new RunId("run_night_retry"), sessionId, workspaceId,
                RunStatus.RUNNING, now.minusSeconds(20), now.minusSeconds(20), "trace_night_retry")
                .withSource(ConversationSourceType.SCHEDULED_TASK, scheduled.taskId().value(), owner);

        NightExecutionTaskRepository tasks = mock(NightExecutionTaskRepository.class);
        NightExecutionRunLifecycleService lifecycle = mock(NightExecutionRunLifecycleService.class);
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        RunApplicationService runs = mock(RunApplicationService.class);
        when(identity.linuxServerId()).thenReturn("linux-night-a");
        when(tasks.findById(scheduled.taskId())).thenReturn(Optional.of(dispatching));
        when(lifecycle.findAcceptedRun(dispatching)).thenReturn(Optional.of(existingRun));
        NightExecutionDispatchService service = new NightExecutionDispatchService(
                tasks, mock(SessionRepository.class), mock(WorkspaceRepository.class),
                mock(ConversationWorkspaceAccessAuthorizer.class),
                mock(UserOpencodeProcessAssignmentService.class),
                mock(ConversationContextApplicationService.class), runs, lifecycle,
                mock(NightExecutionDispatchLeaseGuard.class), identity,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(now, ZoneOffset.UTC),
                () -> "nda_should_not_be_used");

        NightExecutionDispatchBatchResult batch = service.dispatchBatch(
                "linux-night-a", List.of(scheduled.taskId()), "trace_night_retry").block();

        assertThat(batch.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(NightExecutionDispatchStatus.ALREADY_STARTED);
            assertThat(result.runId()).isEqualTo(existingRun.runId().value());
        });
        verify(lifecycle).onAccepted(
                new ScheduledRunMetadata(scheduled.taskId().value(), "nda_existing_attempt"), existingRun);
        verifyNoInteractions(runs);
    }
}
