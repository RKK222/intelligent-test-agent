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
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.opencode.runtime.process.OpencodeScheduledTaskExecutionAffinityProvider;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.enterprise.testagent.opencode.runtime.run.ConversationContextApplicationService;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.run.StartRunInput;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledUserPlanService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证到期任务通过公共进程初始化和现有 Run 链路投递，并释放会话锁。 */
class NightExecutionDispatchServiceTest {

    @Test
    void dispatchesScheduledTaskOnceAndHandsOffToRun() throws Exception {
        Instant now = Instant.parse("2026-07-18T13:01:00Z");
        UserId owner = new UserId("usr_night_dispatch");
        SessionId sessionId = new SessionId("ses_night_dispatch");
        WorkspaceId workspaceId = new WorkspaceId("wrk_night_dispatch");
        ScheduledTaskRunId scheduledRunId = new ScheduledTaskRunId("str_night_dispatch");
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
                Instant.parse("2026-07-18T23:00:00Z"), "linux-night-a", scheduledRunId,
                null, 0, false, null, null, null, null, null, "trace_night_dispatch",
                Instant.parse("2026-07-18T12:00:00Z"), Instant.parse("2026-07-18T12:00:00Z"));

        NightExecutionTaskRepository taskRepository = mock(NightExecutionTaskRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        SessionMessageRepository messageRepository = mock(SessionMessageRepository.class);
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        ConversationWorkspaceAccessAuthorizer access = mock(ConversationWorkspaceAccessAuthorizer.class);
        UserOpencodeProcessAssignmentService assignment = mock(UserOpencodeProcessAssignmentService.class);
        OpencodeScheduledTaskExecutionAffinityProvider affinity = mock(OpencodeScheduledTaskExecutionAffinityProvider.class);
        ConversationContextApplicationService contexts = mock(ConversationContextApplicationService.class);
        RunApplicationService runs = mock(RunApplicationService.class);
        ScheduledUserPlanService userPlans = mock(ScheduledUserPlanService.class);

        when(taskRepository.findByScheduledTaskRunId(scheduledRunId)).thenReturn(Optional.of(task));
        when(taskRepository.claimForScheduledRun(any(), eq(scheduledRunId))).thenReturn(true);
        when(taskRepository.updateIfStatus(any(), eq(NightExecutionTaskStatus.DISPATCHING))).thenReturn(true);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(new Session(
                sessionId, workspaceId, "夜间回归", SessionStatus.ACTIVE, now, now,
                "trace_night_dispatch", null, null, false, ConversationSourceType.MANUAL, null, owner)));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(new Workspace(
                workspaceId, "night", "/tmp/night", WorkspaceStatus.ACTIVE, now, now,
                "linux-night-a", "trace_night_dispatch")));
        when(messageRepository.findBySessionIdAndRemoteMessageId(sessionId, "msg-night-dispatch"))
                .thenReturn(Optional.empty());
        when(assignment.routingLinuxServerId(owner, "opencode")).thenReturn(Optional.of("linux-night-a"));
        when(affinity.currentAffinity()).thenReturn("linux-night-a");
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
        when(runs.startScheduledRun(eq(owner), any(), eq(task.taskId().value()), eq("trace_night_dispatch")))
                .thenReturn(run);

        NightExecutionDispatchService service = new NightExecutionDispatchService(
                taskRepository, sessionRepository, messageRepository, workspaceRepository, access,
                assignment, affinity, contexts, runs, userPlans, objectMapper,
                Clock.fixed(now, ZoneOffset.UTC));
        var result = service.dispatch(new ScheduledTaskContext(
                scheduledRunId, new ScheduledTaskKey("opencode-runtime.night-execution"), null,
                ScheduledTaskTriggerType.USER_PLAN, owner, task.slotStart(),
                "trace_night_dispatch", Map.of()));

        assertThat(result.result()).containsEntry("runId", "run_night_dispatch");
        ArgumentCaptor<NightExecutionTask> claimedTask = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(taskRepository).claimForScheduledRun(claimedTask.capture(), eq(scheduledRunId));
        assertThat(claimedTask.getValue().status()).isEqualTo(NightExecutionTaskStatus.DISPATCHING);
        assertThat(claimedTask.getValue().dispatchStartedAt()).isEqualTo(now);
        verify(assignment).initialize(owner, "opencode", "trace_night_dispatch");
        verify(runs).startScheduledRun(eq(owner), any(), eq(task.taskId().value()), eq("trace_night_dispatch"));
        verify(taskRepository).deleteSessionLock(sessionId, task.taskId());
    }
}
