package com.enterprise.testagent.opencode.runtime.night;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.run.ConversationContextApplicationService;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.run.ScheduledRunMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 目标 Java 的批量入口：认领后只复用普通 Run 启动，不建立夜间专属执行队列。 */
@Service
public class NightExecutionDispatchService {

    public static final int MAX_BATCH_SIZE = 50;
    private static final int START_CONCURRENCY = 4;

    private final NightExecutionTaskRepository taskRepository;
    private final SessionRepository sessionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ConversationWorkspaceAccessAuthorizer accessAuthorizer;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final ConversationContextApplicationService contextService;
    private final RunApplicationService runService;
    private final NightExecutionRunLifecycleService lifecycleService;
    private final NightExecutionDispatchLeaseGuard leaseGuard;
    private final BackendInstanceIdentity backendIdentity;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Supplier<String> attemptIdSupplier;

    @Autowired
    public NightExecutionDispatchService(
            NightExecutionTaskRepository taskRepository,
            SessionRepository sessionRepository,
            WorkspaceRepository workspaceRepository,
            ConversationWorkspaceAccessAuthorizer accessAuthorizer,
            UserOpencodeProcessAssignmentService assignmentService,
            ConversationContextApplicationService contextService,
            RunApplicationService runService,
            NightExecutionRunLifecycleService lifecycleService,
            NightExecutionDispatchLeaseGuard leaseGuard,
            BackendInstanceIdentity backendIdentity,
            ObjectMapper objectMapper,
            Clock clock) {
        this(taskRepository, sessionRepository, workspaceRepository, accessAuthorizer, assignmentService,
                contextService, runService, lifecycleService, leaseGuard, backendIdentity, objectMapper,
                clock, RuntimeIdGenerator::nightExecutionDispatchAttemptId);
    }

    NightExecutionDispatchService(
            NightExecutionTaskRepository taskRepository,
            SessionRepository sessionRepository,
            WorkspaceRepository workspaceRepository,
            ConversationWorkspaceAccessAuthorizer accessAuthorizer,
            UserOpencodeProcessAssignmentService assignmentService,
            ConversationContextApplicationService contextService,
            RunApplicationService runService,
            NightExecutionRunLifecycleService lifecycleService,
            NightExecutionDispatchLeaseGuard leaseGuard,
            BackendInstanceIdentity backendIdentity,
            ObjectMapper objectMapper,
            Clock clock,
            Supplier<String> attemptIdSupplier) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.accessAuthorizer = Objects.requireNonNull(accessAuthorizer);
        this.assignmentService = Objects.requireNonNull(assignmentService);
        this.contextService = Objects.requireNonNull(contextService);
        this.runService = Objects.requireNonNull(runService);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
        this.leaseGuard = Objects.requireNonNull(leaseGuard);
        this.backendIdentity = Objects.requireNonNull(backendIdentity);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.attemptIdSupplier = Objects.requireNonNull(attemptIdSupplier);
    }

    /** boundedElastic 只承载普通 Run 的同步受理调用，批次完成不等待 Run 终态。 */
    public Mono<NightExecutionDispatchBatchResult> dispatchBatch(
            String linuxServerId,
            List<NightExecutionTaskId> taskIds,
            String traceId) {
        String target = requireTarget(linuxServerId);
        List<NightExecutionTaskId> ids = validateIds(taskIds);
        return Flux.fromIterable(ids)
                .flatMapSequential(
                        taskId -> Mono.fromCallable(() -> safeDispatchOne(target, taskId, traceId))
                                .subscribeOn(Schedulers.boundedElastic()),
                        START_CONCURRENCY,
                        1)
                .collectList()
                .map(results -> new NightExecutionDispatchBatchResult(target, results));
    }

    private NightExecutionDispatchResult safeDispatchOne(
            String targetLinuxServerId,
            NightExecutionTaskId taskId,
            String traceId) {
        try {
            return dispatchOne(targetLinuxServerId, taskId, traceId);
        } catch (RuntimeException failure) {
            return result(taskId, NightExecutionDispatchStatus.RETRYABLE_FAILURE, null, "INTERNAL_ERROR");
        }
    }

    private NightExecutionDispatchResult dispatchOne(
            String targetLinuxServerId,
            NightExecutionTaskId taskId,
            String traceId) {
        if (!backendIdentity.linuxServerId().equals(targetLinuxServerId)) {
            return result(taskId, NightExecutionDispatchStatus.TARGET_MISMATCH, null, "TARGET_MISMATCH");
        }
        NightExecutionTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return result(taskId, NightExecutionDispatchStatus.NOT_FOUND, null, null);
        if (!task.targetLinuxServerId().equals(targetLinuxServerId)) {
            return result(taskId, NightExecutionDispatchStatus.TARGET_MISMATCH, null, "TARGET_MISMATCH");
        }
        if (task.status() == NightExecutionTaskStatus.DISPATCHED) {
            return result(taskId, NightExecutionDispatchStatus.ALREADY_STARTED,
                    task.runId() == null ? null : task.runId().value(), null);
        }
        if (task.status() == NightExecutionTaskStatus.DISPATCHING) {
            Optional<Run> existing = lifecycleService.findAcceptedRun(task);
            if (existing.isPresent()) {
                Run run = existing.orElseThrow();
                lifecycleService.onAccepted(
                        new ScheduledRunMetadata(taskId.value(), task.dispatchAttemptId()), run);
                return result(taskId, NightExecutionDispatchStatus.ALREADY_STARTED, run.runId().value(), null);
            }
            return result(taskId, NightExecutionDispatchStatus.IN_PROGRESS, null, null);
        }
        if (task.status() != NightExecutionTaskStatus.SCHEDULED) {
            return result(taskId, NightExecutionDispatchStatus.TERMINAL, null, task.errorCode());
        }
        Instant now = clock.instant();
        if (now.isBefore(task.slotStart())) {
            return result(taskId, NightExecutionDispatchStatus.NOT_DUE, null, null);
        }
        if (!now.isBefore(task.windowEnd())) {
            return result(taskId, NightExecutionDispatchStatus.TERMINAL, null, "WINDOW_EXPIRED");
        }

        String attemptId = attemptIdSupplier.get();
        NightExecutionTask dispatching = task.startDispatch(
                attemptId,
                backendIdentity.backendProcessId(),
                now.plus(NightExecutionDispatchLeaseGuard.LEASE_DURATION),
                now);
        if (!taskRepository.claimForDispatch(dispatching, targetLinuxServerId)) {
            return result(taskId, NightExecutionDispatchStatus.IN_PROGRESS, null, null);
        }
        ScheduledRunMetadata metadata = new ScheduledRunMetadata(taskId.value(), attemptId);
        try (NightExecutionDispatchLeaseGuard.Handle ignored = leaseGuard.track(taskId, attemptId)) {
            Optional<Run> recovered = lifecycleService.findAcceptedRun(dispatching);
            if (recovered.isPresent()) {
                Run run = recovered.orElseThrow();
                lifecycleService.onAccepted(metadata, run);
                return result(taskId, NightExecutionDispatchStatus.ALREADY_STARTED, run.runId().value(), null);
            }
            NightExecutionRunInputSnapshot snapshot = readSnapshot(dispatching);
            Session session = requireSession(dispatching);
            requireWorkspace(dispatching, session);
            Optional<String> binding = assignmentService.routingLinuxServerId(task.ownerUserId(), "opencode");
            if (binding.isPresent() && !binding.orElseThrow().equals(targetLinuxServerId)) {
                PlatformException mismatch = new PlatformException(ErrorCode.CONFLICT, "用户进程归属与固定目标服务器不一致");
                lifecycleService.onRejected(metadata, mismatch);
                return result(taskId, NightExecutionDispatchStatus.TARGET_MISMATCH, null, "TARGET_MISMATCH");
            }
            var process = assignmentService.initialize(task.ownerUserId(), "opencode", traceId);
            if (process.status() != UserOpencodeProcessAvailability.READY
                    || !targetLinuxServerId.equals(process.linuxServerId())) {
                throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 进程启动后仍不可用");
            }
            var issued = contextService.bootstrap(task.ownerUserId(), "opencode", task.sessionId(), traceId);
            Instant runStartAt = clock.instant();
            // Run 副作用前最后一次以 attempt + 窗口做数据库 fencing；07:00 后旧调用不得再创建锚点。
            if (!taskRepository.renewDispatchLease(
                    taskId,
                    attemptId,
                    runStartAt.plus(NightExecutionDispatchLeaseGuard.LEASE_DURATION),
                    runStartAt)) {
                throw new PlatformException(ErrorCode.CONFLICT, "夜间任务分发租约已失效");
            }
            Run run = runService.startScheduledRun(
                    task.ownerUserId(),
                    snapshot.toStartRunInput(task.sessionId(), issued.contextToken()),
                    metadata,
                    traceId);
            return result(taskId, NightExecutionDispatchStatus.STARTED, run.runId().value(), null);
        } catch (PlatformException failure) {
            lifecycleService.onRejected(metadata, failure);
            return result(taskId,
                    permanent(failure.errorCode())
                            ? NightExecutionDispatchStatus.PERMANENT_FAILURE
                            : NightExecutionDispatchStatus.RETRYABLE_FAILURE,
                    null,
                    failure.errorCode().name());
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            PlatformException invalid = new PlatformException(
                    ErrorCode.VALIDATION_ERROR, "夜间任务输入已失效", java.util.Map.of(), failure);
            lifecycleService.onRejected(metadata, invalid);
            return result(taskId, NightExecutionDispatchStatus.PERMANENT_FAILURE, null, "INVALID_RUN_INPUT");
        } catch (RuntimeException failure) {
            lifecycleService.onRejected(metadata, failure);
            return result(taskId, NightExecutionDispatchStatus.RETRYABLE_FAILURE, null, "INTERNAL_ERROR");
        }
    }

    private NightExecutionRunInputSnapshot readSnapshot(NightExecutionTask task) throws JsonProcessingException {
        if (task.runInputJson() == null || task.runInputJson().isBlank()) {
            throw new IllegalArgumentException("run input missing");
        }
        return objectMapper.readValue(task.runInputJson(), NightExecutionRunInputSnapshot.class);
    }

    private Session requireSession(NightExecutionTask task) {
        Session session = sessionRepository.findById(task.sessionId())
                .filter(item -> item.status() == SessionStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "会话不存在"));
        if (session.createdByUserId() != null && !session.createdByUserId().equals(task.ownerUserId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "夜间任务无权访问会话");
        }
        if (!session.workspaceId().equals(task.workspaceId())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "会话与 Workspace 不匹配");
        }
        return session;
    }

    private void requireWorkspace(NightExecutionTask task, Session session) {
        var workspace = workspaceRepository.findById(session.workspaceId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在"));
        accessAuthorizer.requireAccess(task.ownerUserId(), workspace.workspaceId());
    }

    private boolean permanent(ErrorCode code) {
        return code == ErrorCode.NOT_FOUND
                || code == ErrorCode.FORBIDDEN
                || code == ErrorCode.VALIDATION_ERROR;
    }

    private NightExecutionDispatchResult result(
            NightExecutionTaskId taskId,
            NightExecutionDispatchStatus status,
            String runId,
            String errorCode) {
        return new NightExecutionDispatchResult(taskId, status, runId, errorCode);
    }

    private String requireTarget(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "linuxServerId 不能为空");
        }
        return value.trim();
    }

    private List<NightExecutionTaskId> validateIds(List<NightExecutionTaskId> taskIds) {
        if (taskIds == null || taskIds.isEmpty() || taskIds.size() > MAX_BATCH_SIZE) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "taskIds 数量必须为 1 到 50");
        }
        if (taskIds.stream().anyMatch(Objects::isNull)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }
        return List.copyOf(taskIds);
    }
}
