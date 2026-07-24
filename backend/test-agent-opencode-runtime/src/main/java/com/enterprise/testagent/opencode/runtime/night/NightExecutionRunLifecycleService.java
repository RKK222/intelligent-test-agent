package com.enterprise.testagent.opencode.runtime.night;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunSummaryPersistencePort;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.opencode.runtime.run.ScheduledRunLifecycleObserver;
import com.enterprise.testagent.opencode.runtime.run.ScheduledRunMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 把普通 Run 的受理边界收敛到夜间任务状态；Run 后续终态与本服务无关。 */
@Service
public class NightExecutionRunLifecycleService implements ScheduledRunLifecycleObserver {

    private final NightExecutionTaskRepository taskRepository;
    private final SessionMessageRepository messageRepository;
    private final RunSummaryPersistencePort runSummaryPersistencePort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NightExecutionRunLifecycleService(
            NightExecutionTaskRepository taskRepository,
            SessionMessageRepository messageRepository,
            RunSummaryPersistencePort runSummaryPersistencePort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.runSummaryPersistencePort = Objects.requireNonNull(runSummaryPersistencePort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public void onAccepted(ScheduledRunMetadata metadata, Run run) {
        if (metadata.dispatchAttemptId() == null) return;
        NightExecutionTask task = task(metadata.sourceRefId()).orElse(null);
        if (!sameAttempt(task, metadata.dispatchAttemptId())) return;
        Instant now = clock.instant();
        NightExecutionTask dispatched = task.dispatched(run.runId(), now);
        if (taskRepository.updateDispatchIfAttempt(dispatched, metadata.dispatchAttemptId())) {
            taskRepository.deleteSessionLock(task.sessionId(), task.taskId());
            releaseCapacity(task, now);
        }
    }

    @Override
    @Transactional
    public void onRejected(ScheduledRunMetadata metadata, RuntimeException failure) {
        if (metadata.dispatchAttemptId() == null) return;
        NightExecutionTask task = task(metadata.sourceRefId()).orElse(null);
        if (!sameAttempt(task, metadata.dispatchAttemptId())) return;
        Optional<Run> existing = findAcceptedRun(task);
        if (existing.isPresent()) {
            onAccepted(metadata, existing.orElseThrow());
            return;
        }
        Instant now = clock.instant();
        if (permanent(failure)) {
            NightExecutionTask failed = task.fail(errorCode(failure), safeMessage(failure), now);
            if (taskRepository.updateDispatchIfAttempt(failed, metadata.dispatchAttemptId())) {
                taskRepository.deleteSessionLock(task.sessionId(), task.taskId());
                releaseCapacity(task, now);
            }
            return;
        }
        taskRepository.updateDispatchIfAttempt(task.retryDispatch(now), metadata.dispatchAttemptId());
    }

    /** 测试定时不占用公共夜间时段，完成或失败时只释放会话锁。 */
    private void releaseCapacity(NightExecutionTask task, Instant now) {
        if (task.scheduleMode().reservesNightCapacity()) {
            taskRepository.releaseSlot(task.slotStart(), now);
        }
    }

    /** 优先使用 Redis/legacy 关系型唯一锚点；仅对没有数据库锚点的历史数据兼容查询用户消息。 */
    public Optional<Run> findAcceptedRun(NightExecutionTask task) {
        NightExecutionRunInputSnapshot snapshot = snapshot(task);
        String runClientRequestId = snapshot == null || snapshot.clientRequestId() == null
                ? task.clientRequestId()
                : snapshot.clientRequestId();
        Optional<com.enterprise.testagent.domain.run.RunPersistenceAnchor> persisted = runSummaryPersistencePort
                .findBySessionAndClientRequestId(task.sessionId(), runClientRequestId);
        if (persisted.isPresent()) {
            // 已有关系型锚点时禁止退回“用户消息即受理”的历史判定，否则 anchor-only 崩溃窗口仍会丢执行。
            return persisted
                    .filter(anchor -> belongsToTask(task, anchor.sourceType(), anchor.sourceRefId(),
                            anchor.triggeredByUserId(), anchor.workspaceId()))
                    .filter(anchor -> anchor.storageMode()
                                    != com.enterprise.testagent.domain.run.RunStorageMode.LEGACY_FULL
                            || anchor.scheduledDispatchAcceptedAt() != null
                            || anchor.status().isTerminal())
                    .map(anchor -> new Run(
                            anchor.runId(), anchor.sessionId(), anchor.workspaceId(), anchor.status(),
                            anchor.createdAt(), anchor.updatedAt(), anchor.traceId()));
        }
        String messageId = snapshot == null ? null : snapshot.messageId();
        if (messageId == null) return Optional.empty();
        return messageRepository.findBySessionIdAndRemoteMessageId(task.sessionId(), messageId)
                .filter(message -> belongsToTask(task, message.sourceType(), message.sourceRefId(),
                        message.senderUserId(), task.workspaceId()))
                .filter(message -> message.runId() != null)
                .map(message -> new Run(
                        message.runId(), task.sessionId(), task.workspaceId(), RunStatus.RUNNING,
                        message.createdAt(), message.updatedAt(), task.traceId()));
    }

    /** 恢复锚点必须精确属于当前任务，客户端复用历史 ID 时不得误释放会话锁和容量。 */
    private boolean belongsToTask(
            NightExecutionTask task,
            com.enterprise.testagent.domain.session.ConversationSourceType sourceType,
            String sourceRefId,
            com.enterprise.testagent.domain.user.UserId owner,
            com.enterprise.testagent.domain.workspace.WorkspaceId workspaceId) {
        return sourceType == com.enterprise.testagent.domain.session.ConversationSourceType.SCHEDULED_TASK
                && task.taskId().value().equals(sourceRefId)
                && task.ownerUserId().equals(owner)
                && task.workspaceId().equals(workspaceId);
    }

    private Optional<NightExecutionTask> task(String sourceRefId) {
        try {
            return taskRepository.findById(new NightExecutionTaskId(sourceRefId));
        } catch (IllegalArgumentException invalidId) {
            return Optional.empty();
        }
    }

    private boolean sameAttempt(NightExecutionTask task, String attemptId) {
        return task != null
                && task.status() == NightExecutionTaskStatus.DISPATCHING
                && attemptId.equals(task.dispatchAttemptId());
    }

    private NightExecutionRunInputSnapshot snapshot(NightExecutionTask task) {
        if (task.runInputJson() == null || task.runInputJson().isBlank()) return null;
        try {
            return objectMapper.readValue(task.runInputJson(), NightExecutionRunInputSnapshot.class);
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean permanent(RuntimeException failure) {
        return failure instanceof PlatformException platform
                && (platform.errorCode() == ErrorCode.NOT_FOUND
                || platform.errorCode() == ErrorCode.FORBIDDEN
                || platform.errorCode() == ErrorCode.VALIDATION_ERROR);
    }

    private String errorCode(RuntimeException failure) {
        return failure instanceof PlatformException platform
                ? platform.errorCode().name()
                : "INVALID_RUN_INPUT";
    }

    private String safeMessage(RuntimeException failure) {
        if (!(failure instanceof PlatformException platform)) return "夜间任务输入已失效";
        return switch (platform.errorCode()) {
            case NOT_FOUND -> "会话或 Workspace 已不存在";
            case FORBIDDEN -> "夜间任务执行权限已失效";
            case VALIDATION_ERROR -> "夜间任务输入无效";
            default -> "夜间任务启动失败";
        };
    }
}
