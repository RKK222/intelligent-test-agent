package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.nightexecution.NightExecutionScheduleMode;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
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
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.run.StartRunInput;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证提交夜间任务时复用当前会话、占用容量并加会话锁，不创建旧 USER_PLAN。 */
class NightExecutionTaskApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final Instant SLOT = Instant.parse("2026-07-18T13:00:00Z");
    private static final Instant CUSTOM_SLOT = Instant.parse("2026-07-18T12:01:00Z");
    private static final UserId USER = new UserId("usr_night_service");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_night_service");
    private static final SessionId SESSION_ID = new SessionId("ses_night_service");

    private NightExecutionTaskRepository taskRepository;
    private SessionRepository sessionRepository;
    private WorkspaceRepository workspaceRepository;
    private ConversationWorkspaceAccessAuthorizer accessAuthorizer;
    private UserOpencodeProcessAssignmentService assignmentService;
    private NightExecutionCapacityRegistry capacityRegistry;
    private NightExecutionTaskApplicationService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(NightExecutionTaskRepository.class);
        sessionRepository = mock(SessionRepository.class);
        workspaceRepository = mock(WorkspaceRepository.class);
        accessAuthorizer = mock(ConversationWorkspaceAccessAuthorizer.class);
        assignmentService = mock(UserOpencodeProcessAssignmentService.class);
        SessionMessageRepository messageRepository = mock(SessionMessageRepository.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);

        capacityRegistry = mock(NightExecutionCapacityRegistry.class);
        when(capacityRegistry.currentCapacity()).thenReturn(2);
        when(taskRepository.findByOwnerAndClientRequestId(USER, "request-night-service"))
                .thenReturn(Optional.empty());
        when(taskRepository.reservationCounts(any(), any())).thenReturn(Map.of());
        when(taskRepository.reserveSlot(SLOT, 2, NOW)).thenReturn(true);
        when(taskRepository.insertSessionLock(eq(SESSION_ID), any(), eq(USER), eq(NOW))).thenReturn(true);
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(existingSession()));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(assignmentService.routingLinuxServerId(USER, "opencode")).thenReturn(Optional.of("linux-night-a"));
        when(routeResolver.currentLinuxServerIdValue()).thenReturn("linux-night-current");

        service = new NightExecutionTaskApplicationService(
                taskRepository, sessionRepository, messageRepository, workspaceRepository,
                accessAuthorizer, assignmentService, routeResolver,
                new NightExecutionWindowCalculator(), capacityRegistry,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsTaskForExistingSessionWithoutCreatingUserPlan() {
        NightExecutionTask created = service.create(
                USER,
                false,
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
        assertThat(created.scheduledTaskRunId()).isNull();
        assertThat(created.contentPreview()).isEqualTo("生成回归测试");
        verify(accessAuthorizer).requireAccess(USER, WORKSPACE_ID);
        verify(taskRepository).reserveSlot(SLOT, 2, NOW);

        ArgumentCaptor<NightExecutionTask> saved = ArgumentCaptor.forClass(NightExecutionTask.class);
        verify(taskRepository).save(saved.capture());
        assertThat(saved.getValue().scheduledTaskRunId()).isNull();
    }

    @Test
    void createsAScheduledSourceSessionForABlankConversation() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.insertSessionLock(any(SessionId.class), any(), eq(USER), eq(NOW))).thenReturn(true);

        NightExecutionTask created = service.create(
                USER,
                false,
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
    void slotQueryDoesNotDependOnSchedulerAvailability() {
        assertThat(service.slots().capacity()).isEqualTo(2);
    }

    @Test
    void slotQueriesUseLatestCapacitySnapshotWithoutCancellingExistingReservations() {
        when(taskRepository.reservationCounts(any(), any())).thenReturn(Map.of(SLOT, 2));
        when(capacityRegistry.currentCapacity()).thenReturn(2, 3);

        var atLowerCapacity = service.slots();
        var atHigherCapacity = service.slots();

        assertThat(atLowerCapacity.capacity()).isEqualTo(2);
        assertThat(atLowerCapacity.slots()).filteredOn(slot -> slot.slotStart().equals(SLOT))
                .singleElement()
                .satisfies(slot -> assertThat(slot.available()).isFalse());
        assertThat(atHigherCapacity.capacity()).isEqualTo(3);
        assertThat(atHigherCapacity.slots()).filteredOn(slot -> slot.slotStart().equals(SLOT))
                .singleElement()
                .satisfies(slot -> assertThat(slot.available()).isTrue());
    }

    @Test
    void capacityRaceReturnsLatestSlotsForImmediateReselection() {
        when(taskRepository.reservationCounts(any(), any()))
                .thenReturn(Map.of(), Map.of(SLOT, 2));
        when(taskRepository.reserveSlot(SLOT, 2, NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                        USER,
                        false,
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

    @Test
    void rejectsCustomScheduleBeforeAnyMutationWhenCallerIsNotSuperAdmin() {
        assertThatThrownBy(() -> service.create(
                        USER, false, customCommand(CUSTOM_SLOT), "trace_night_service"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(taskRepository, never()).lockCreateRequest(USER, "request-night-custom");
        verify(sessionRepository, never()).save(any());
        verify(taskRepository, never()).reserveSlot(any(), anyInt(), any());
    }

    @Test
    void idempotentRetryReturnsExistingCustomTaskBeforeExpiredTimeValidation() {
        NightExecutionTask existing = existingCustomTask();
        when(taskRepository.findByOwnerAndClientRequestId(USER, "request-night-custom"))
                .thenReturn(Optional.of(existing));

        NightExecutionTask retried = service.create(
                USER, true, customCommand(NOW.minusSeconds(60)), "trace_night_service_retry");

        assertThat(retried).isSameAs(existing);
        verify(taskRepository, never()).lockCreateRequest(USER, "request-night-custom");
        verify(sessionRepository, never()).save(any());
        verify(taskRepository, never()).reserveSlot(any(), anyInt(), any());
    }

    @Test
    void idempotentRetryReturnsExistingNightTaskBeforeCapacityRevalidation() {
        NightExecutionTask existing = existingNightTask();
        when(taskRepository.findByOwnerAndClientRequestId(USER, "request-night-service"))
                .thenReturn(Optional.of(existing));
        when(taskRepository.reservationCounts(any(), any())).thenReturn(Map.of(SLOT, 2));

        NightExecutionTask retried = service.create(
                USER,
                false,
                new NightExecutionCreateCommand(
                        "request-night-service", SESSION_ID, WORKSPACE_ID, "回归测试",
                        new NightExecutionRunInputSnapshot(
                                "生成回归测试", List.of(StartRunInput.PromptPart.text("生成回归测试")),
                                "msg-night-service", "build", null, null, "build",
                                null, null, "run-request-night-service"),
                        SLOT),
                "trace_night_service_retry");

        assertThat(retried).isSameAs(existing);
        verify(taskRepository, never()).lockCreateRequest(USER, "request-night-service");
        verify(taskRepository, never()).reserveSlot(any(), anyInt(), any());
    }

    @Test
    void createsDaytimeCustomScheduleWithoutReadingOrReservingNightCapacity() {
        when(taskRepository.findByOwnerAndClientRequestId(USER, "request-night-custom"))
                .thenReturn(Optional.empty());

        NightExecutionTask created = service.create(
                USER, true, customCommand(CUSTOM_SLOT), "trace_night_service");

        assertThat(created.scheduleMode()).isEqualTo(NightExecutionScheduleMode.ADMIN_CUSTOM);
        assertThat(created.slotStart()).isEqualTo(CUSTOM_SLOT);
        assertThat(created.slotEnd()).isEqualTo(CUSTOM_SLOT.plusSeconds(60));
        assertThat(created.windowEnd()).isEqualTo(CUSTOM_SLOT.plusSeconds(900));
        verify(capacityRegistry, never()).currentCapacity();
        verify(taskRepository, never()).reserveSlot(any(), anyInt(), any());
    }

    @Test
    void adjustsAndCancelsCustomScheduleWithoutTouchingNightCapacity() {
        when(taskRepository.findByOwnerAndClientRequestId(USER, "request-night-custom"))
                .thenReturn(Optional.empty());
        NightExecutionTask created = service.create(
                USER, true, customCommand(CUSTOM_SLOT), "trace_night_service");
        clearInvocations(taskRepository, capacityRegistry);
        when(taskRepository.findById(created.taskId())).thenReturn(Optional.of(created));
        when(taskRepository.updateIfStatus(any(), eq(NightExecutionTaskStatus.SCHEDULED))).thenReturn(true);
        Instant adjustedStart = CUSTOM_SLOT.plusSeconds(120);

        NightExecutionTask adjusted = service.adjust(
                USER, true, created.taskId(), adjustedStart, "trace_night_service");

        assertThat(adjusted.scheduleMode()).isEqualTo(NightExecutionScheduleMode.ADMIN_CUSTOM);
        assertThat(adjusted.slotEnd()).isEqualTo(adjustedStart.plusSeconds(60));
        assertThat(adjusted.windowEnd()).isEqualTo(adjustedStart.plusSeconds(900));
        verify(taskRepository, never()).reserveSlot(any(), anyInt(), any());
        verify(taskRepository, never()).releaseSlot(any(), any());

        clearInvocations(taskRepository);
        when(taskRepository.findById(created.taskId())).thenReturn(Optional.of(created));
        service.cancel(USER, created.taskId(), "trace_night_service");
        verify(taskRepository).deleteSessionLock(created.sessionId(), created.taskId());
        verify(taskRepository, never()).releaseSlot(any(), any());
    }

    @Test
    void roleRemovalBlocksCustomAdjustmentButDoesNotBlockCancellation() {
        when(taskRepository.findByOwnerAndClientRequestId(USER, "request-night-custom"))
                .thenReturn(Optional.empty());
        NightExecutionTask created = service.create(
                USER, true, customCommand(CUSTOM_SLOT), "trace_night_service");
        clearInvocations(taskRepository);
        when(taskRepository.findById(created.taskId())).thenReturn(Optional.of(created));

        assertThatThrownBy(() -> service.adjust(
                        USER, false, created.taskId(), CUSTOM_SLOT.plusSeconds(120), "trace_night_service"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(taskRepository, never()).updateIfStatus(any(), any());

        when(taskRepository.updateIfStatus(any(), eq(NightExecutionTaskStatus.SCHEDULED))).thenReturn(true);
        service.cancel(USER, created.taskId(), "trace_night_service");
        verify(taskRepository).deleteSessionLock(created.sessionId(), created.taskId());
    }

    private NightExecutionCreateCommand customCommand(Instant slotStart) {
        return new NightExecutionCreateCommand(
                "request-night-custom", SESSION_ID, WORKSPACE_ID, "测试定时",
                new NightExecutionRunInputSnapshot(
                        "生成回归测试", List.of(StartRunInput.PromptPart.text("生成回归测试")),
                        "msg-night-custom", "build", null, null, "build",
                        null, null, "run-request-night-custom"),
                NightExecutionScheduleMode.ADMIN_CUSTOM, slotStart);
    }

    private NightExecutionTask existingCustomTask() {
        return new NightExecutionTask(
                new NightExecutionTaskId("net_existing_custom"), USER, SESSION_ID, WORKSPACE_ID,
                "request-night-custom", "测试定时", "生成回归测试", "{}",
                NightExecutionScheduleMode.ADMIN_CUSTOM, NightExecutionTaskStatus.SCHEDULED,
                CUSTOM_SLOT, CUSTOM_SLOT.plusSeconds(60), CUSTOM_SLOT.plusSeconds(900),
                "linux-night-a", null, null, 0, false, null, null, null, null, 0L,
                null, null, null, null, "trace_existing_custom", NOW, NOW);
    }

    private NightExecutionTask existingNightTask() {
        return new NightExecutionTask(
                new NightExecutionTaskId("net_existing_night"), USER, SESSION_ID, WORKSPACE_ID,
                "request-night-service", "夜间回归", "生成回归测试", "{}",
                NightExecutionTaskStatus.SCHEDULED, SLOT, SLOT.plusSeconds(900),
                Instant.parse("2026-07-18T23:00:00Z"), "linux-night-a", null, null, 0,
                false, null, null, null, null, null, "trace_existing_night", NOW, NOW);
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
