package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
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
import com.enterprise.testagent.opencode.runtime.run.StartRunInput;
import com.enterprise.testagent.scheduler.ScheduledUserPlanService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证提交夜间任务时复用当前会话、占用容量、加会话锁并写入 USER_PLAN。 */
class NightExecutionTaskApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final Instant SLOT = Instant.parse("2026-07-18T13:00:00Z");
    private static final UserId USER = new UserId("usr_night_service");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_night_service");
    private static final SessionId SESSION_ID = new SessionId("ses_night_service");

    private NightExecutionTaskRepository taskRepository;
    private SessionRepository sessionRepository;
    private WorkspaceRepository workspaceRepository;
    private ConversationWorkspaceAccessAuthorizer accessAuthorizer;
    private UserOpencodeProcessAssignmentService assignmentService;
    private ScheduledUserPlanService scheduledUserPlanService;
    private NightExecutionTaskApplicationService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(NightExecutionTaskRepository.class);
        sessionRepository = mock(SessionRepository.class);
        workspaceRepository = mock(WorkspaceRepository.class);
        accessAuthorizer = mock(ConversationWorkspaceAccessAuthorizer.class);
        assignmentService = mock(UserOpencodeProcessAssignmentService.class);
        scheduledUserPlanService = mock(ScheduledUserPlanService.class);
        SessionMessageRepository messageRepository = mock(SessionMessageRepository.class);
        OpencodeScheduledTaskExecutionAffinityProvider affinityProvider =
                mock(OpencodeScheduledTaskExecutionAffinityProvider.class);

        NightExecutionProperties properties = new NightExecutionProperties();
        properties.setSlotCapacity("2");
        when(taskRepository.findByOwnerAndClientRequestId(USER, "request-night-service"))
                .thenReturn(Optional.empty());
        when(taskRepository.reservationCounts(any(), any())).thenReturn(Map.of());
        when(taskRepository.reserveSlot(SLOT, 2, NOW)).thenReturn(true);
        when(taskRepository.insertSessionLock(eq(SESSION_ID), any(), eq(USER), eq(NOW))).thenReturn(true);
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduledUserPlanService.available()).thenReturn(true);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(existingSession()));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(assignmentService.routingLinuxServerId(USER, "opencode")).thenReturn(Optional.of("linux-night-a"));
        when(affinityProvider.currentAffinity()).thenReturn("linux-night-current");
        when(scheduledUserPlanService.schedule(any(), eq(USER), eq(SLOT), eq("linux-night-a"), any()))
                .thenReturn(ScheduledTaskRun.pending(
                        new ScheduledTaskRunId("str_night_service"),
                        new ScheduledTaskKey("opencode-runtime.night-execution"), null,
                        ScheduledTaskTriggerType.USER_PLAN, USER, SLOT, "linux-night-a",
                        "trace_night_service", NOW));

        service = new NightExecutionTaskApplicationService(
                taskRepository, sessionRepository, messageRepository, workspaceRepository,
                accessAuthorizer, assignmentService, scheduledUserPlanService, affinityProvider,
                new NightExecutionWindowCalculator(), properties,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsTaskForExistingSessionAndBindsScheduledRun() {
        NightExecutionTask created = service.create(
                USER,
                new NightExecutionCreateCommand(
                        "request-night-service", SESSION_ID, WORKSPACE_ID, "回归测试",
                        new NightExecutionRunInputSnapshot(
                                "生成回归测试", List.of(StartRunInput.PromptPart.text("生成回归测试")),
                                "msg-night-service", "build", "provider/model", null, "build",
                                null, null, "run-request-night-service"),
                        SLOT),
                "trace_night_service");

        assertThat(created.status()).isEqualTo(NightExecutionTaskStatus.SCHEDULED);
        assertThat(created.sessionId()).isEqualTo(SESSION_ID);
        assertThat(created.targetLinuxServerId()).isEqualTo("linux-night-a");
        assertThat(created.scheduledTaskRunId()).isEqualTo(new ScheduledTaskRunId("str_night_service"));
        assertThat(created.contentPreview()).isEqualTo("生成回归测试");
        verify(accessAuthorizer).requireAccess(USER, WORKSPACE_ID);
        verify(taskRepository).reserveSlot(SLOT, 2, NOW);

        ArgumentCaptor<NightExecutionTask> saved = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(taskRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().getLast().scheduledTaskRunId().value()).isEqualTo("str_night_service");
    }

    @Test
    void createsAScheduledSourceSessionForABlankConversation() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.insertSessionLock(any(SessionId.class), any(), eq(USER), eq(NOW))).thenReturn(true);

        NightExecutionTask created = service.create(
                USER,
                new NightExecutionCreateCommand(
                        "request-night-service", null, WORKSPACE_ID, "新的夜间会话",
                        new NightExecutionRunInputSnapshot(
                                "生成回归测试", List.of(StartRunInput.PromptPart.text("生成回归测试")),
                                null, "build", null, null, "build",
                                null, null, null),
                        SLOT),
                "trace_night_service");

        assertThat(created.taskCreatedSession()).isTrue();
        assertThat(created.sessionId()).isNotEqualTo(SESSION_ID);
        ArgumentCaptor<Session> session = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(session.capture());
        assertThat(session.getValue().sourceType()).isEqualTo(ConversationSourceType.SCHEDULED_TASK);
        assertThat(session.getValue().sourceRefId()).isEqualTo(created.taskId().value());
        assertThat(session.getValue().createdByUserId()).isEqualTo(USER);
        verify(taskRepository).insertSessionLock(created.sessionId(), created.taskId(), USER, NOW);
    }

    @Test
    void slotQueryFailsClosedWhenUserPlanRunnerIsUnavailable() {
        when(scheduledUserPlanService.available()).thenReturn(false);

        assertThatThrownBy(service::slots)
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NIGHT_EXECUTION_UNAVAILABLE));
    }

    @Test
    void capacityRaceReturnsLatestSlotsForImmediateReselection() {
        when(taskRepository.reservationCounts(any(), any()))
                .thenReturn(Map.of(), Map.of(SLOT, 2));
        when(taskRepository.reserveSlot(SLOT, 2, NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                        USER,
                        new NightExecutionCreateCommand(
                                "request-night-service", SESSION_ID, WORKSPACE_ID, "回归测试",
                                new NightExecutionRunInputSnapshot(
                                        "生成回归测试", List.of(StartRunInput.PromptPart.text("生成回归测试")),
                                        "msg-night-service", "build", "provider/model", null, "build",
                                        null, null, "run-request-night-service"),
                                SLOT),
                        "trace_night_service"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details())
                            .containsEntry("timeZone", "Asia/Shanghai")
                            .containsEntry("capacity", 2)
                            .containsKeys("windowStart", "windowEnd", "slots");
                    assertThat((List<?>) exception.details().get("slots")).isNotEmpty();
                });
    }

    private Session existingSession() {
        return new Session(
                SESSION_ID, WORKSPACE_ID, "已有会话", SessionStatus.ACTIVE, NOW, NOW,
                "trace_night_service", null, null, false,
                ConversationSourceType.MANUAL, null, USER);
    }

    private Workspace workspace() {
        return new Workspace(
                WORKSPACE_ID, "night", "/tmp/night", WorkspaceStatus.ACTIVE, NOW, NOW,
                "linux-night-a", "trace_night_service");
    }
}
