package com.enterprise.testagent.opencode.runtime.night;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionScheduleMode;
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
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 夜间任务提交、查询、改期、取消和失败卡关闭的统一应用服务。 */
@Service
public class NightExecutionTaskApplicationService {

    private final NightExecutionTaskRepository taskRepository;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ConversationWorkspaceAccessAuthorizer accessAuthorizer;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final BackendJavaRouteResolver routeResolver;
    private final NightExecutionWindowCalculator windowCalculator;
    private final NightExecutionCapacityRegistry capacityRegistry;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NightExecutionTaskApplicationService(
            NightExecutionTaskRepository taskRepository,
            SessionRepository sessionRepository,
            SessionMessageRepository messageRepository,
            WorkspaceRepository workspaceRepository,
            ConversationWorkspaceAccessAuthorizer accessAuthorizer,
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            NightExecutionWindowCalculator windowCalculator,
            NightExecutionCapacityRegistry capacityRegistry,
            ObjectMapper objectMapper,
            Clock clock) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.accessAuthorizer = Objects.requireNonNull(accessAuthorizer);
        this.assignmentService = Objects.requireNonNull(assignmentService);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.windowCalculator = Objects.requireNonNull(windowCalculator);
        this.capacityRegistry = Objects.requireNonNull(capacityRegistry);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 返回下一夜间窗口；先计算边界再查询该窗口的真实占用。 */
    public NightExecutionWindowCalculator.NightExecutionWindow slots() {
        int capacity = capacityRegistry.currentCapacity();
        Instant now = clock.instant();
        var window = windowCalculator.nextWindow(now, Map.of(), capacity);
        Map<Instant, Integer> reservations =
                taskRepository.reservationCounts(window.windowStart(), window.windowEnd());
        return windowCalculator.nextWindow(now, reservations, capacity);
    }

    /** 幂等创建夜间任务；空白对话的 Session、占位和锁同属一个数据库事务。 */
    @Transactional
    public NightExecutionTask create(
            UserId owner,
            boolean superAdmin,
            NightExecutionCreateCommand command,
            String traceId) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(command, "command must not be null");
        requireCustomPermission(command.scheduleMode(), superAdmin);
        NightExecutionTask existing = taskRepository
                .findByOwnerAndClientRequestId(owner, command.clientRequestId()).orElse(null);
        if (existing != null) return existing;

        Instant now = clock.instant();
        TaskSchedule schedule = scheduleForCreate(command, now);
        taskRepository.lockCreateRequest(owner, command.clientRequestId());
        existing = taskRepository
                .findByOwnerAndClientRequestId(owner, command.clientRequestId()).orElse(null);
        if (existing != null) return existing;

        Workspace workspace = workspaceRepository.findById(command.workspaceId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在"));
        accessAuthorizer.requireAccess(owner, workspace.workspaceId());

        NightExecutionTaskId taskId = new NightExecutionTaskId(RuntimeIdGenerator.nightExecutionTaskId());
        SessionResolution session = resolveSession(owner, command, taskId, traceId, now);
        if (schedule.reservesCapacity()
                && !taskRepository.reserveSlot(schedule.slotStart(), schedule.capacity(), now)) {
            throw slotConflict("所选夜间时段刚刚已满，请重新选择", slots());
        }

        NightExecutionRunInputSnapshot snapshot = command.runInput().withStableIds();
        String targetLinuxServerId = assignmentService.routingLinuxServerId(owner, "opencode")
                .orElseGet(routeResolver::currentLinuxServerIdValue);
        NightExecutionTask draft = new NightExecutionTask(
                taskId, owner, session.session().sessionId(), workspace.workspaceId(), command.clientRequestId(),
                session.session().title(), preview(snapshot.effectivePrompt()), writeSnapshot(snapshot),
                command.scheduleMode(), NightExecutionTaskStatus.SCHEDULED,
                schedule.slotStart(), schedule.slotEnd(), schedule.windowEnd(),
                targetLinuxServerId, null, null, 0, session.created(),
                null, null, null, null, 0L, null, null, null, null,
                traceId, now, now);
        taskRepository.save(draft);
        if (!taskRepository.insertSessionLock(session.session().sessionId(), taskId, owner, now)) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前会话已有待执行夜间任务");
        }
        return draft;
    }

    /** 集中任务页分页；按 sessionId 查询时同时恢复最近未关闭失败卡。 */
    public NightExecutionTaskQueryResult list(UserId owner, SessionId sessionId, PageRequest pageRequest) {
        if (sessionId == null) {
            return new NightExecutionTaskQueryResult(
                    taskRepository.findPendingByOwner(owner, pageRequest), null);
        }
        NightExecutionTask pending = taskRepository.findPendingBySession(sessionId)
                .filter(task -> task.ownerUserId().equals(owner)).orElse(null);
        List<NightExecutionTask> items = pending == null ? List.of() : List.of(pending);
        return new NightExecutionTaskQueryResult(
                new PageResponse<>(items, 1, pageRequest.size(), items.size()),
                taskRepository.findVisibleFailureBySession(owner, sessionId).orElse(null));
    }

    /** 用户改期只允许尚未认领的任务；新占位写入成功后才释放旧时段。 */
    @Transactional
    public NightExecutionTask adjust(
            UserId owner,
            boolean superAdmin,
            NightExecutionTaskId taskId,
            Instant slotStart,
            String traceId) {
        NightExecutionTask current = owned(owner, taskId);
        requireScheduled(current, "任务已经开始，无法调整时段");
        requireCustomPermission(current.scheduleMode(), superAdmin);
        if (current.slotStart().equals(slotStart)) return current;
        Instant now = clock.instant();
        if (current.scheduleMode() == NightExecutionScheduleMode.ADMIN_CUSTOM) {
            var custom = NightExecutionCustomSchedulePolicy.resolve(slotStart, now);
            NightExecutionTask adjusted = current.reschedule(
                    custom.slotStart(), custom.slotEnd(), custom.windowEnd(),
                    current.targetLinuxServerId(), now);
            if (!taskRepository.updateIfStatus(adjusted, NightExecutionTaskStatus.SCHEDULED)) {
                throw new PlatformException(ErrorCode.CONFLICT, "任务已经开始，无法调整时段");
            }
            return adjusted;
        }
        var window = slots();
        if (!window.windowEnd().equals(current.windowEnd())) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前夜间窗口已结束，不能再调整时段");
        }
        var selected = selectableSlot(slotStart, window);
        if (!taskRepository.reserveSlot(selected.slotStart(), window.capacity(), now)) {
            throw slotConflict("所选夜间时段刚刚已满，请重新选择", slots());
        }
        NightExecutionTask adjusted = current.reschedule(
                selected.slotStart(), selected.slotEnd(), current.windowEnd(),
                current.targetLinuxServerId(), now);
        if (!taskRepository.updateIfStatus(adjusted, NightExecutionTaskStatus.SCHEDULED)) {
            throw new PlatformException(ErrorCode.CONFLICT, "任务已经开始，无法调整时段");
        }
        releaseCapacity(current, now);
        return adjusted;
    }

    /** 取消任务后释放会话锁；专门创建且仍为空的 Session 自动归档。 */
    @Transactional
    public NightExecutionTask cancel(UserId owner, NightExecutionTaskId taskId, String traceId) {
        NightExecutionTask current = owned(owner, taskId);
        requireScheduled(current, "任务已经开始，无法取消");
        Instant now = clock.instant();
        NightExecutionTask cancelled = current.cancel(now);
        if (!taskRepository.updateIfStatus(cancelled, NightExecutionTaskStatus.SCHEDULED)) {
            throw new PlatformException(ErrorCode.CONFLICT, "任务已经开始，无法取消");
        }
        taskRepository.deleteSessionLock(current.sessionId(), current.taskId());
        releaseCapacity(current, now);
        archiveEmptyCreatedSession(current, traceId, now);
        return cancelled;
    }

    /** 关闭最终失败卡；重复请求幂等，空 Session 在同一事务中归档。 */
    @Transactional
    public NightExecutionTask dismiss(UserId owner, NightExecutionTaskId taskId, String traceId) {
        NightExecutionTask current = owned(owner, taskId);
        if (current.status() != NightExecutionTaskStatus.FAILED) {
            throw new PlatformException(ErrorCode.CONFLICT, "只有启动失败的夜间任务可以关闭");
        }
        if (current.dismissedAt() != null) return current;
        Instant now = clock.instant();
        NightExecutionTask dismissed = current.dismiss(now);
        if (!taskRepository.updateIfStatus(dismissed, NightExecutionTaskStatus.FAILED)) {
            throw new PlatformException(ErrorCode.CONFLICT, "夜间任务状态已变化");
        }
        archiveEmptyCreatedSession(current, traceId, now);
        return dismissed;
    }

    private SessionResolution resolveSession(
            UserId owner,
            NightExecutionCreateCommand command,
            NightExecutionTaskId taskId,
            String traceId,
            Instant now) {
        if (command.sessionId() != null) {
            Session session = sessionRepository.findById(command.sessionId())
                    .filter(item -> item.status() == SessionStatus.ACTIVE)
                    .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "会话不存在"));
            if (!session.workspaceId().equals(command.workspaceId())) {
                throw new PlatformException(ErrorCode.CONFLICT, "会话与 Workspace 不匹配");
            }
            if (session.createdByUserId() != null && !session.createdByUserId().equals(owner)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "无权在该会话创建夜间任务");
            }
            return new SessionResolution(session, false);
        }
        String title = command.sessionTitle() == null
                ? preview(command.runInput().effectivePrompt(), 72)
                : command.sessionTitle();
        Session session = new Session(
                new SessionId(RuntimeIdGenerator.sessionId()), command.workspaceId(), title,
                SessionStatus.ACTIVE, now, now, traceId)
                .withSource(ConversationSourceType.SCHEDULED_TASK, taskId.value(), owner);
        return new SessionResolution(sessionRepository.save(session), true);
    }

    private NightExecutionWindowCalculator.NightExecutionSlot selectableSlot(
            Instant slotStart,
            NightExecutionWindowCalculator.NightExecutionWindow window) {
        return window.slots().stream()
                .filter(slot -> slot.slotStart().equals(slotStart) && slot.available())
                .findFirst()
                .orElseThrow(() -> slotConflict("所选夜间时段不可用，请重新选择", window));
    }

    /** 创建前完成权限和时间校验，确保非法测试请求不产生幂等锁、Session 或容量副作用。 */
    private TaskSchedule scheduleForCreate(
            NightExecutionCreateCommand command,
            Instant now) {
        if (command.scheduleMode() == NightExecutionScheduleMode.ADMIN_CUSTOM) {
            var custom = NightExecutionCustomSchedulePolicy.resolve(command.slotStart(), now);
            return new TaskSchedule(
                    custom.slotStart(), custom.slotEnd(), custom.windowEnd(), 0, false);
        }
        var window = slots();
        var selected = selectableSlot(command.slotStart(), window);
        return new TaskSchedule(
                selected.slotStart(), selected.slotEnd(), window.windowEnd(), window.capacity(), true);
    }

    private void requireCustomPermission(NightExecutionScheduleMode mode, boolean superAdmin) {
        if (mode == NightExecutionScheduleMode.ADMIN_CUSTOM && !superAdmin) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "仅超级管理员可使用测试定时");
        }
    }

    private void releaseCapacity(NightExecutionTask task, Instant now) {
        if (task.scheduleMode().reservesNightCapacity()) {
            taskRepository.releaseSlot(task.slotStart(), now);
        }
    }

    /** 容量冲突同时返回最新窗口，前端可立即刷新选择器而无需猜测可用时段。 */
    private PlatformException slotConflict(
            String message,
            NightExecutionWindowCalculator.NightExecutionWindow window) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                message,
                Map.of(
                        "timeZone", window.timeZone(),
                        "windowStart", window.windowStart(),
                        "windowEnd", window.windowEnd(),
                        "capacity", window.capacity(),
                        "slots", window.slots()));
    }

    private NightExecutionTask owned(UserId owner, NightExecutionTaskId taskId) {
        NightExecutionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "夜间任务不存在"));
        if (!task.ownerUserId().equals(owner)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "夜间任务不存在");
        }
        return task;
    }

    private void requireScheduled(NightExecutionTask task, String message) {
        if (task.status() != NightExecutionTaskStatus.SCHEDULED) {
            throw new PlatformException(ErrorCode.CONFLICT, message);
        }
    }

    private void archiveEmptyCreatedSession(NightExecutionTask task, String traceId, Instant now) {
        if (!task.taskCreatedSession()) return;
        if (messageRepository.findBySessionId(task.sessionId(), new PageRequest(1, 1)).total() > 0) return;
        sessionRepository.findById(task.sessionId())
                .filter(session -> session.status() == SessionStatus.ACTIVE)
                .ifPresent(session -> sessionRepository.save(session.archive(now, traceId)));
    }

    private String writeSnapshot(NightExecutionRunInputSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "夜间任务输入保存失败");
        }
    }

    private static String preview(String value) {
        return preview(value, 200);
    }

    private static String preview(String value, int maxCodePoints) {
        String normalized = value == null ? "" : value.strip();
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= maxCodePoints) return normalized.isBlank() ? "新对话" : normalized;
        int end = normalized.offsetByCodePoints(0, maxCodePoints);
        return normalized.substring(0, end) + "…";
    }

    private record SessionResolution(Session session, boolean created) { }

    /** 两种模式统一交给聚合的持久时间边界；容量字段仅对标准夜间模式有意义。 */
    private record TaskSchedule(
            Instant slotStart,
            Instant slotEnd,
            Instant windowEnd,
            int capacity,
            boolean reservesCapacity) { }
}
