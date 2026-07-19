package com.enterprise.testagent.opencode.runtime.night;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.opencode.runtime.process.OpencodeScheduledTaskExecutionAffinityProvider;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.run.ConversationContextApplicationService;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import com.enterprise.testagent.scheduler.ScheduledUserPlanService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** USER_PLAN 到期投递器：公共进程启动、上下文签发和既有 Run 编排在这里串联。 */
@Service
public class NightExecutionDispatchService {

    private final NightExecutionTaskRepository taskRepository;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ConversationWorkspaceAccessAuthorizer accessAuthorizer;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final OpencodeScheduledTaskExecutionAffinityProvider affinityProvider;
    private final ConversationContextApplicationService contextService;
    private final RunApplicationService runService;
    private final ScheduledUserPlanService userPlanService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private TransactionTemplate transactionTemplate;

    public NightExecutionDispatchService(
            NightExecutionTaskRepository taskRepository,
            SessionRepository sessionRepository,
            SessionMessageRepository messageRepository,
            WorkspaceRepository workspaceRepository,
            ConversationWorkspaceAccessAuthorizer accessAuthorizer,
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeScheduledTaskExecutionAffinityProvider affinityProvider,
            ConversationContextApplicationService contextService,
            RunApplicationService runService,
            ScheduledUserPlanService userPlanService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.accessAuthorizer = Objects.requireNonNull(accessAuthorizer);
        this.assignmentService = Objects.requireNonNull(assignmentService);
        this.affinityProvider = Objects.requireNonNull(affinityProvider);
        this.contextService = Objects.requireNonNull(contextService);
        this.runService = Objects.requireNonNull(runService);
        this.userPlanService = Objects.requireNonNull(userPlanService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 重复扫描只允许当前 scheduledTaskRunId 把 SCHEDULED 条件更新为 DISPATCHING。 */
    public ScheduledTaskResult dispatch(ScheduledTaskContext context) {
        NightExecutionTask task = taskRepository.findByScheduledTaskRunId(context.taskRunId()).orElse(null);
        if (task == null || task.status() != NightExecutionTaskStatus.SCHEDULED) {
            return ScheduledTaskResult.of(Map.of("alreadyHandled", true));
        }
        Instant now = clock.instant();
        NightExecutionTask dispatching = task.startDispatch(now);
        // 单条条件更新立即提交认领，不把进程启动和远端 Run 创建放进数据库长事务。
        if (!taskRepository.claimForScheduledRun(dispatching, context.taskRunId())) {
            return ScheduledTaskResult.of(Map.of("alreadyHandled", true));
        }
        if (context.requestedByUserId() != null && !context.requestedByUserId().equals(task.ownerUserId())) {
            return failFinal(dispatching, NightExecutionTaskStatus.DISPATCHING,
                    "OWNER_MISMATCH", "夜间任务发起人校验失败", context.traceId());
        }
        if (!now.isBefore(task.windowEnd())) {
            return failFinal(dispatching, NightExecutionTaskStatus.DISPATCHING,
                    "WINDOW_EXPIRED", "夜间执行窗口已结束", context.traceId());
        }

        try {
            NightExecutionRunInputSnapshot snapshot = readSnapshot(dispatching);
            Run recovered = recoverExistingRun(dispatching, snapshot);
            if (recovered != null) {
                return complete(dispatching, recovered, now);
            }
            Session session = requireSession(dispatching);
            requireWorkspace(dispatching, session);

            String currentAffinity = affinityProvider.currentAffinity();
            String bindingAffinity = assignmentService.routingLinuxServerId(task.ownerUserId(), "opencode")
                    .orElse(currentAffinity);
            if (!bindingAffinity.equals(currentAffinity)) {
                return reroute(dispatching, bindingAffinity, now, context.traceId());
            }

            var process = assignmentService.initialize(task.ownerUserId(), "opencode", context.traceId());
            if (process.status() != UserOpencodeProcessAvailability.READY) {
                throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 进程启动后仍不可用");
            }
            var issued = contextService.bootstrap(
                    task.ownerUserId(), "opencode", task.sessionId(), context.traceId());
            Run run = runService.startScheduledRun(
                    task.ownerUserId(),
                    snapshot.toStartRunInput(task.sessionId(), issued.contextToken()),
                    task.taskId().value(),
                    context.traceId());
            return complete(dispatching, run, clock.instant());
        } catch (PlatformException exception) {
            if (permanent(exception.errorCode())) {
                return failFinal(
                        dispatching,
                        NightExecutionTaskStatus.DISPATCHING,
                        exception.errorCode().name(),
                        safeMessage(exception),
                        context.traceId());
            }
            // 临时故障保留 DISPATCHING，周期 reconcile 会按 5 分钟租约恢复或顺延。
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return failFinal(dispatching, NightExecutionTaskStatus.DISPATCHING,
                    "INVALID_RUN_INPUT", "夜间任务输入已失效", context.traceId());
        }
    }

    private Run recoverExistingRun(NightExecutionTask task, NightExecutionRunInputSnapshot snapshot) {
        if (snapshot.messageId() == null) return null;
        return messageRepository.findBySessionIdAndRemoteMessageId(task.sessionId(), snapshot.messageId())
                .filter(message -> message.runId() != null)
                .map(message -> new Run(
                        message.runId(), task.sessionId(), task.workspaceId(),
                        com.enterprise.testagent.domain.run.RunStatus.RUNNING,
                        message.createdAt(), message.updatedAt(), task.traceId()))
                .orElse(null);
    }

    private Session requireSession(NightExecutionTask task) {
        Session session = sessionRepository.findById(task.sessionId())
                .filter(item -> item.status() == SessionStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "会话不存在"));
        if (session.createdByUserId() != null && !session.createdByUserId().equals(task.ownerUserId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "夜间任务无权访问会话");
        }
        if (!session.workspaceId().equals(task.workspaceId())) {
            // 保存后的会话归属变化属于不可重试的数据失效，立即终止并释放夜间容量。
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "会话与 Workspace 不匹配");
        }
        return session;
    }

    private Workspace requireWorkspace(NightExecutionTask task, Session session) {
        Workspace workspace = workspaceRepository.findById(session.workspaceId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在"));
        accessAuthorizer.requireAccess(task.ownerUserId(), workspace.workspaceId());
        return workspace;
    }

    private ScheduledTaskResult reroute(
            NightExecutionTask task,
            String targetAffinity,
            Instant now,
            String traceId) {
        return inTransaction(() -> {
            var newRun = userPlanService.schedule(
                    NightExecutionTaskApplicationService.TASK_KEY,
                    task.ownerUserId(),
                    now.isAfter(task.slotStart()) ? now : task.slotStart(),
                    targetAffinity,
                    traceId);
            NightExecutionTask rerouted = task.reschedule(
                    task.slotStart(), task.slotEnd(), targetAffinity, newRun.taskRunId(), now);
            if (!taskRepository.updateIfStatus(rerouted, NightExecutionTaskStatus.DISPATCHING)) {
                throw new PlatformException(ErrorCode.CONFLICT, "夜间任务路由状态已变化");
            }
            return ScheduledTaskResult.of(Map.of("rerouted", true, "linuxServerId", targetAffinity));
        });
    }

    private ScheduledTaskResult complete(NightExecutionTask task, Run run, Instant now) {
        return inTransaction(() -> {
            NightExecutionTask dispatched = task.dispatched(run.runId(), now);
            if (!taskRepository.updateIfStatus(dispatched, NightExecutionTaskStatus.DISPATCHING)) {
                throw new PlatformException(ErrorCode.CONFLICT, "夜间任务投递状态已变化");
            }
            taskRepository.deleteSessionLock(task.sessionId(), task.taskId());
            return ScheduledTaskResult.of(Map.of("runId", run.runId().value(), "dispatched", true));
        });
    }

    private ScheduledTaskResult failFinal(
            NightExecutionTask task,
            NightExecutionTaskStatus expectedStatus,
            String errorCode,
            String errorMessage,
            String traceId) {
        return inTransaction(() -> {
            Instant now = clock.instant();
            NightExecutionTask failed = task.fail(errorCode, errorMessage, now);
            if (taskRepository.updateIfStatus(failed, expectedStatus)) {
                taskRepository.deleteSessionLock(task.sessionId(), task.taskId());
                taskRepository.releaseSlot(task.slotStart(), now);
            }
            return ScheduledTaskResult.of(Map.of(
                    "failed", true,
                    "errorCode", errorCode,
                    "traceId", traceId));
        });
    }

    /** 生产环境把多语句状态迁移收敛为短事务；手工构造的单元测试直接执行同一逻辑。 */
    @Autowired(required = false)
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private <T> T inTransaction(Supplier<T> action) {
        TransactionTemplate template = transactionTemplate;
        if (template == null) return action.get();
        return Objects.requireNonNull(template.execute(status -> action.get()));
    }

    private NightExecutionRunInputSnapshot readSnapshot(NightExecutionTask task) throws JsonProcessingException {
        if (task.runInputJson() == null || task.runInputJson().isBlank()) {
            throw new IllegalArgumentException("run input missing");
        }
        return objectMapper.readValue(task.runInputJson(), NightExecutionRunInputSnapshot.class);
    }

    private boolean permanent(ErrorCode code) {
        return code == ErrorCode.NOT_FOUND
                || code == ErrorCode.FORBIDDEN
                || code == ErrorCode.VALIDATION_ERROR;
    }

    private String safeMessage(PlatformException exception) {
        return switch (exception.errorCode()) {
            case NOT_FOUND -> "会话或 Workspace 已不存在";
            case FORBIDDEN -> "夜间任务执行权限已失效";
            case VALIDATION_ERROR -> "夜间任务输入无效";
            default -> "夜间任务启动失败";
        };
    }
}
