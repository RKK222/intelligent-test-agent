package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAtomicMutationPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAssignmentConflictException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStep;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProxyRuntimeSettings;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerCommandNotDispatchedException;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 公共 opencode server 启动服务，统一执行 start/restart 后的进程快照、健康确认和兼容节点投影。
 *
 * <p>所有会拉起 opencode server 的入口都应调用本服务，避免只收到 manager `STARTED` 就写入
 * `RUNNING`，从而把尚未通过 HTTP health 的进程暴露给前端或 Run 链路。
 */
@Service
public class OpencodeProcessStartupService {

    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final String OPENCODE_REFERENCES_DIR_PARAM = "OPENCODE_REFERENCES_DIR";
    private static final Duration DEFAULT_STARTUP_HEALTH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STARTUP_HEALTH_POLL_INTERVAL = Duration.ofMillis(500);

    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessAtomicMutationPort atomicMutationPort;
    private final ExecutionNodeRepository executionNodeRepository;
    private final OpencodeProcessManagerGateway gateway;
    private final OpencodeProcessStatusQueryService statusQueryService;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final Clock clock;
    private final Duration startupHealthTimeout;
    private final Duration startupHealthPollInterval;
    private final Consumer<Duration> startupHealthSleeper;
    private final InternalModelProxyRuntimeSettings internalProxySettings;
    private final UserRepository userRepository;
    private final ConversationContextStore conversationContextStore;
    private final CommonParameterValues commonParameterValues;
    private OpencodeProcessConfigLinkService configLinkService;
    private OpencodeProcessStopService stopService;

    /** 启动前把用户有效公共配置链接到共享运行副本；方法注入保持既有测试构造器兼容。 */
    @Autowired
    void setConfigLinkService(OpencodeProcessConfigLinkService configLinkService) {
        this.configLinkService = Objects.requireNonNull(configLinkService, "configLinkService must not be null");
    }

    /** Spring 生产路径复用统一停止服务执行启动竞争补偿。 */
    @Autowired
    void setStopService(OpencodeProcessStopService stopService) {
        this.stopService = Objects.requireNonNull(stopService, "stopService must not be null");
    }

    /**
     * Spring 生产构造器使用系统 UTC 时钟。
     */
    @Autowired
    public OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStatusQueryService statusQueryService,
            OpencodeProcessAtomicMutationPort atomicMutationPort,
            ManagerControlSettings managerControlSettings,
            InternalModelProxyRuntimeSettings internalProxySettings,
            UserRepository userRepository,
            ConversationContextStore conversationContextStore,
            CommonParameterValues commonParameterValues) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                statusQueryService,
                atomicMutationPort,
                Clock.systemUTC(),
                managerControlSettings.commandTimeout(),
                DEFAULT_STARTUP_HEALTH_POLL_INTERVAL,
                OpencodeProcessStartupService::sleepCurrentThread,
                internalProxySettings,
                userRepository,
                conversationContextStore,
                commonParameterValues);
    }

    /**
     * 兼容旧测试或手工装配入口；生产路径由 Spring 注入公共状态查询服务。
     */
    public OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                (OpencodeProcessStatusQueryService) null,
                Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，保证进程快照时间稳定。
     */
    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                (OpencodeProcessStatusQueryService) null,
                clock);
    }

    /**
     * 测试构造器允许验证目标 Java 平台的通用参数解析与启动环境合并。
     */
    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock,
            CommonParameterValues commonParameterValues) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                null,
                clock,
                DEFAULT_STARTUP_HEALTH_TIMEOUT,
                DEFAULT_STARTUP_HEALTH_POLL_INTERVAL,
                duration -> { },
                null,
                null,
                null,
                commonParameterValues);
    }

    /**
     * 测试构造器允许验证进程重新启动后的上下文失效。
     */
    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ConversationContextStore conversationContextStore,
            Clock clock) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                null,
                clock,
                DEFAULT_STARTUP_HEALTH_TIMEOUT,
                DEFAULT_STARTUP_HEALTH_POLL_INTERVAL,
                duration -> { },
                null,
                null,
                conversationContextStore);
    }

    /**
     * 完整测试构造器允许替换公共状态查询服务。
     */
    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStatusQueryService statusQueryService,
            Clock clock) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                statusQueryService,
                clock,
                DEFAULT_STARTUP_HEALTH_TIMEOUT,
                DEFAULT_STARTUP_HEALTH_POLL_INTERVAL,
                duration -> { });
    }

    /**
     * 完整测试构造器允许控制启动后健康确认窗口，避免单测真实等待。
     */
    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStatusQueryService statusQueryService,
            Clock clock,
            Duration startupHealthTimeout,
            Duration startupHealthPollInterval,
            Consumer<Duration> startupHealthSleeper) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                statusQueryService,
                clock,
                startupHealthTimeout,
                startupHealthPollInterval,
                startupHealthSleeper,
                null,
                null,
                null);
    }

    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStatusQueryService statusQueryService,
            Clock clock,
            Duration startupHealthTimeout,
            Duration startupHealthPollInterval,
            Consumer<Duration> startupHealthSleeper,
            InternalModelProxyRuntimeSettings internalProxySettings,
            UserRepository userRepository,
            ConversationContextStore conversationContextStore) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                statusQueryService,
                clock,
                startupHealthTimeout,
                startupHealthPollInterval,
                startupHealthSleeper,
                internalProxySettings,
                userRepository,
                conversationContextStore,
                null);
    }

    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStatusQueryService statusQueryService,
            Clock clock,
            Duration startupHealthTimeout,
            Duration startupHealthPollInterval,
            Consumer<Duration> startupHealthSleeper,
            InternalModelProxyRuntimeSettings internalProxySettings,
            UserRepository userRepository,
            ConversationContextStore conversationContextStore,
            CommonParameterValues commonParameterValues) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                statusQueryService,
                new RepositoryBackedOpencodeProcessAtomicMutationPort(repository),
                clock,
                startupHealthTimeout,
                startupHealthPollInterval,
                startupHealthSleeper,
                internalProxySettings,
                userRepository,
                conversationContextStore,
                commonParameterValues);
    }

    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStatusQueryService statusQueryService,
            OpencodeProcessAtomicMutationPort atomicMutationPort,
            Clock clock,
            Duration startupHealthTimeout,
            Duration startupHealthPollInterval,
            Consumer<Duration> startupHealthSleeper,
            InternalModelProxyRuntimeSettings internalProxySettings,
            UserRepository userRepository,
            ConversationContextStore conversationContextStore,
            CommonParameterValues commonParameterValues) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.atomicMutationPort = Objects.requireNonNull(atomicMutationPort, "atomicMutationPort must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.heartbeatStore = heartbeatStore;
        this.statusQueryService = statusQueryService == null
                ? new OpencodeProcessStatusQueryService(
                        repository,
                        gateway,
                        heartbeatStore,
                        atomicMutationPort,
                        clock)
                : statusQueryService;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.startupHealthTimeout = positive(startupHealthTimeout, DEFAULT_STARTUP_HEALTH_TIMEOUT);
        this.startupHealthPollInterval = positive(startupHealthPollInterval, DEFAULT_STARTUP_HEALTH_POLL_INTERVAL);
        this.startupHealthSleeper = Objects.requireNonNull(startupHealthSleeper, "startupHealthSleeper must not be null");
        this.internalProxySettings = internalProxySettings;
        this.userRepository = userRepository;
        this.conversationContextStore = conversationContextStore;
        this.commonParameterValues = commonParameterValues;
        this.stopService = new OpencodeProcessStopService(
                gateway,
                repository,
                this.statusQueryService,
                userRepository);
    }

    /**
     * 调用 manager start 后立即确认健康；只有 health healthy 才返回 RUNNING 进程。
     */
    public OpencodeServerProcess startAndVerify(OpencodeProcessStartupRequest request) {
        return startAndVerify(request, OpencodeProcessStartProgress.noop());
    }

    /**
     * 调用 manager start 后立即确认健康，并按可选 operation 记录启动公共链路进度。
     */
    public OpencodeServerProcess startAndVerify(
            OpencodeProcessStartupRequest request,
            OpencodeProcessStartProgress progress) {
        OpencodeProcessStartProgress resolvedProgress = progress == null ? OpencodeProcessStartProgress.noop() : progress;
        Optional<OpencodeServerProcess> expectedExisting = expectedExistingAssignment(request);
        try {
            resolvedProgress.step(OpencodeProcessStartOperationStep.STARTING_PROCESS);
            if (configLinkService != null) {
                configLinkService.switchToShared(request.sessionPath(), request.configPath());
            }
            OpencodeProcessStartCommand command = startCommand(request);
            OpencodeProcessStartResult started = gateway.startProcess(command);
            if (started == null) {
                throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程启动未返回结果");
            }
            OpencodeServerProcess candidate = startupCandidate(request, started.pid(), started.message());
            try {
                return markStartedAndVerify(request, candidate, resolvedProgress, expectedExisting);
            } catch (OpencodeProcessAssignmentConflictException exception) {
                PlatformException conflict = new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "TestAgent 进程分配已变化，拒绝旧启动结果回写");
                try {
                    compensateFreshConflictingStart(command, started, candidate);
                } catch (RuntimeException compensationFailure) {
                    // 补偿失败不能把并发冲突伪装成其它业务结果；保留异常供日志/诊断读取。
                    conflict.addSuppressed(compensationFailure);
                }
                throw conflict;
            }
        } catch (ManagerCommandNotDispatchedException exception) {
            // 命令尚未写入任何 manager 连接，候选选择层可安全尝试下一个容器。
            throw exception;
        } catch (PlatformException exception) {
            resolvedProgress.failed(exception);
            throw exception;
        } catch (RuntimeException exception) {
            resolvedProgress.failed(exception);
            throw exception;
        }
    }

    /**
     * 在外部 restart 已返回 STARTED 后，复用同一套候选快照、health 和最终状态回写逻辑。
     */
    public OpencodeServerProcess markStartedAndVerify(
            OpencodeProcessStartupRequest request,
            Long pid,
            String startMessage) {
        return markStartedAndVerify(request, pid, startMessage, OpencodeProcessStartProgress.noop());
    }

    /**
     * 在外部 restart 已返回 STARTED 后，复用同一套候选快照、health 和最终状态回写逻辑。
     */
    public OpencodeServerProcess markStartedAndVerify(
            OpencodeProcessStartupRequest request,
            Long pid,
            String startMessage,
            OpencodeProcessStartProgress progress) {
        return markStartedAndVerify(
                request,
                pid,
                startMessage,
                progress,
                expectedExistingAssignment(request));
    }

    private OpencodeServerProcess markStartedAndVerify(
            OpencodeProcessStartupRequest request,
            Long pid,
            String startMessage,
            OpencodeProcessStartProgress progress,
            Optional<OpencodeServerProcess> expectedExisting) {
        return markStartedAndVerify(
                request,
                startupCandidate(request, pid, startMessage),
                progress,
                expectedExisting);
    }

    private OpencodeServerProcess markStartedAndVerify(
            OpencodeProcessStartupRequest request,
            OpencodeServerProcess candidate,
            OpencodeProcessStartProgress progress,
            Optional<OpencodeServerProcess> expectedExisting) {
        OpencodeProcessStartProgress resolvedProgress = progress == null ? OpencodeProcessStartProgress.noop() : progress;
        resolvedProgress.step(OpencodeProcessStartOperationStep.SAVING_CANDIDATE);
        if (expectedExisting.isPresent()) {
            if (!atomicMutationPort.compareAndSetRuntimeState(expectedExisting.get(), candidate)) {
                throw new OpencodeProcessAssignmentConflictException("TestAgent 进程分配已变化，拒绝旧启动结果回写");
            }
        } else {
            // 仅保留旧公共 API 的首次创建兼容；正常用户初始化已在短事务中预留 process/binding。
            repository.saveOpencodeServerProcess(candidate);
        }
        OpencodeProcessStatusProbe probe = waitForStartupHealth(candidate, request.traceId(), resolvedProgress);
        if (probe.status() != OpencodeProcessProbeStatus.RUNNING) {
            ErrorCode errorCode = probe.errorCode() == null ? ErrorCode.OPENCODE_UNAVAILABLE : probe.errorCode();
            persistFailedStartup(candidate, probe, request.traceId());
            resolvedProgress.failed(errorCode.name(), startupFailureMessage(probe));
            throw new PlatformException(
                    errorCode,
                    startupFailureMessage(probe),
                    Map.of("processId", candidate.processId().value(), "port", candidate.port()));
        }
        OpencodeServerProcess running = probe.process().orElse(candidate);
        if (!atomicMutationPort.compareAndSetRuntimeState(candidate, running)) {
            throw new OpencodeProcessAssignmentConflictException("TestAgent 进程分配已变化，拒绝旧健康结果回写");
        }
        heartbeatStore.recordOpencodeHeartbeat(running.processId(), probe.checkedAt());
        if (conversationContextStore != null) {
            conversationContextStore.invalidateProcess(running.processId().value());
        }
        resolvedProgress.step(OpencodeProcessStartOperationStep.SAVING_BINDING);
        if (expectedExisting.isPresent()) {
            requireMatchingBinding(running);
        } else {
            repository.saveUserBinding(new UserOpencodeProcessBinding(
                    running.userId(),
                    OPENCODE_AGENT_ID,
                    running.processId(),
                    running.linuxServerId(),
                    running.port(),
                    UserOpencodeProcessBindingStatus.ACTIVE,
                    request.bindingCreatedAt() == null ? running.createdAt() : request.bindingCreatedAt(),
                    running.updatedAt(),
                    request.traceId()));
        }
        executionNodeRepository.save(projectExecutionNode(running, running.updatedAt(), request.traceId()));
        return running;
    }

    private OpencodeServerProcess startupCandidate(
            OpencodeProcessStartupRequest request,
            Long pid,
            String startMessage) {
        Instant now = Instant.now(clock);
        OpencodeProcessId processId = request.processId() == null
                ? new OpencodeProcessId(RuntimeIdGenerator.opencodeProcessId())
                : request.processId();
        Instant createdAt = request.createdAt() == null ? now : request.createdAt();
        return new OpencodeServerProcess(
                processId,
                request.userId(),
                request.linuxServerId(),
                request.containerId(),
                request.port(),
                pid,
                request.baseUrl(),
                OpencodeServerProcessStatus.STARTING,
                request.sessionPath(),
                request.configPath(),
                now,
                now,
                startMessage == null || startMessage.isBlank() ? "started" : startMessage,
                createdAt,
                now,
                request.traceId());
    }

    /** 仅 manager 明确报告本次新建进程时，才允许按启动回包的精确身份执行补偿。 */
    private void compensateFreshConflictingStart(
            OpencodeProcessStartCommand command,
            OpencodeProcessStartResult started,
            OpencodeServerProcess candidate) {
        if (!Boolean.TRUE.equals(started.processCreated())) {
            return;
        }
        if (started.pid() == null || started.pid() < 1 || command.unifiedAuthId() == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "TestAgent 新建实例缺少精确身份，无法安全执行启动补偿");
        }
        stopService.stopStartedInstanceAndVerify(
                candidate,
                command.unifiedAuthId(),
                started.pid(),
                command.traceId());
    }

    private OpencodeProcessStatusProbe waitForStartupHealth(OpencodeServerProcess candidate, String traceId) {
        return waitForStartupHealth(candidate, traceId, OpencodeProcessStartProgress.noop());
    }

    private OpencodeProcessStatusProbe waitForStartupHealth(
            OpencodeServerProcess candidate,
            String traceId,
            OpencodeProcessStartProgress progress) {
        int maxAttempts = startupHealthMaxAttempts();
        Instant deadline = Instant.now(clock).plus(startupHealthTimeout);
        OpencodeProcessStatusProbe probe = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            progress.step(OpencodeProcessStartOperationStep.CHECKING_PROCESS);
            probe = statusQueryService.querySnapshotReadOnly(candidate, traceId);
            if (probe.status() != OpencodeProcessProbeStatus.NOT_STARTED) {
                progress.step(OpencodeProcessStartOperationStep.HEALTH_CHECKING);
            }
            if (!shouldRetryStartupHealth(probe)
                    || attempt == maxAttempts
                    || !Instant.now(clock).isBefore(deadline)) {
                return probe;
            }
            startupHealthSleeper.accept(startupHealthPollInterval);
        }
        return probe;
    }

    /**
     * opencode HTTP 尚未 ready（STALE 或普通 HEALTH_CHECK_FAILED）才等待；
     * manager 控制面错误或进程不存在立即失败。
     */
    private boolean shouldRetryStartupHealth(OpencodeProcessStatusProbe probe) {
        // 只有 OpenCode HTTP 暂未就绪才等待；Manager 超时/网关错误直接失败。
        if (probe.status() == OpencodeProcessProbeStatus.STALE) {
            return probe.errorCode() == ErrorCode.OPENCODE_UNAVAILABLE;
        }
        // 普通健康失败（非控制面错误）也需要等待
        return probe.status() == OpencodeProcessProbeStatus.HEALTH_CHECK_FAILED && probe.errorCode() == null;
    }

    /**
     * 启动确认失败时收敛候选快照，避免异常返回后数据库长期残留 STARTING。
     */
    private void persistFailedStartup(
            OpencodeServerProcess candidate,
            OpencodeProcessStatusProbe probe,
            String traceId) {
        OpencodeServerProcess failed;
        if (probe.status() == OpencodeProcessProbeStatus.NOT_STARTED) {
            failed = probe.process().orElseGet(() -> failedStartupSnapshot(
                    candidate,
                    OpencodeServerProcessStatus.STOPPED,
                    null,
                    probe,
                    traceId));
        } else {
            OpencodeServerProcess current = probe.process().orElse(candidate);
            OpencodeServerProcessStatus status =
                    probe.errorCode() == null || probe.errorCode() == ErrorCode.OPENCODE_UNAVAILABLE
                            ? OpencodeServerProcessStatus.UNHEALTHY
                            : OpencodeServerProcessStatus.FAILED;
            failed = failedStartupSnapshot(current, status, current.pid(), probe, traceId);
        }
        if (!atomicMutationPort.compareAndSetRuntimeState(candidate, failed)) {
            throw new OpencodeProcessAssignmentConflictException("TestAgent 进程分配已变化，拒绝旧失败结果回写");
        }
    }

    private OpencodeServerProcess failedStartupSnapshot(
            OpencodeServerProcess current,
            OpencodeServerProcessStatus status,
            Long pid,
            OpencodeProcessStatusProbe probe,
            String traceId) {
        Instant checkedAt = probe.checkedAt();
        return new OpencodeServerProcess(
                current.processId(),
                current.userId(),
                current.linuxServerId(),
                current.containerId(),
                current.port(),
                pid,
                current.baseUrl(),
                status,
                current.sessionPath(),
                current.configPath(),
                current.startedAt(),
                checkedAt,
                probe.message(),
                current.createdAt(),
                checkedAt,
                traceId);
    }

    /** manager 外部调用前固定既有权威 assignment，并验证 process/binding 当前一致。 */
    private Optional<OpencodeServerProcess> expectedExistingAssignment(OpencodeProcessStartupRequest request) {
        if (request.processId() == null) {
            return Optional.empty();
        }
        Optional<OpencodeServerProcess> existing = repository.findOpencodeServerProcessById(request.processId());
        if (existing.isEmpty()) {
            // 兼容旧调用方携带预生成 processId、但尚未创建平台记录的首次启动。
            return Optional.empty();
        }
        OpencodeServerProcess process = existing.get();
        if (!matchesRequest(process, request)) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 启动目标已被并发修改");
        }
        requireMatchingBinding(process);
        return Optional.of(process);
    }

    private void requireMatchingBinding(OpencodeServerProcess process) {
        Optional<UserOpencodeProcessBinding> current = repository.findUserBinding(process.userId(), OPENCODE_AGENT_ID);
        if (current.isEmpty()) {
            current = Optional.ofNullable(repository
                    .findUserBindingsByProcessIds(java.util.List.of(process.processId()))
                    .get(process.processId()));
        }
        if (current.isEmpty()) {
            // 运行管理允许操作尚未建立用户 binding 的历史平台进程，但绝不在这里隐式创建 binding。
            return;
        }
        UserOpencodeProcessBinding binding = current.get();
        if (binding.status() != UserOpencodeProcessBindingStatus.ACTIVE
                || !binding.processId().equals(process.processId())
                || !binding.linuxServerId().equals(process.linuxServerId())
                || binding.port() != process.port()) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 用户绑定已被并发修改");
        }
    }

    private boolean matchesRequest(
            OpencodeServerProcess process,
            OpencodeProcessStartupRequest request) {
        return process.userId().equals(request.userId())
                && process.linuxServerId().equals(request.linuxServerId())
                && process.containerId().equals(request.containerId())
                && process.port() == request.port();
    }

    private int startupHealthMaxAttempts() {
        long timeoutMillis = startupHealthTimeout.toMillis();
        if (timeoutMillis <= 0) {
            return 1;
        }
        long pollMillis = Math.max(1L, startupHealthPollInterval.toMillis());
        long attempts = (timeoutMillis + pollMillis - 1) / pollMillis + 1;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, attempts));
    }

    private String startupFailureMessage(OpencodeProcessStatusProbe probe) {
        String message = probe.message() == null || probe.message().isBlank() ? "TestAgent 健康检测异常" : probe.message();
        if (shouldRetryStartupHealth(probe) && startupHealthTimeout.toMillis() > 0) {
            return "启动后 " + formatDuration(startupHealthTimeout) + "内未通过健康检查：" + message;
        }
        return message;
    }

    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis % 1000 == 0) {
            return (millis / 1000) + " 秒";
        }
        return millis + " 毫秒";
    }

    private OpencodeProcessStartCommand startCommand(OpencodeProcessStartupRequest request) {
        return new OpencodeProcessStartCommand(
                request.userId(),
                logUnifiedAuthId(request),
                request.linuxServerId(),
                request.containerId(),
                request.port(),
                request.baseUrl(),
                request.sessionPath(),
                request.configPath(),
                startupEnvironment(request),
                request.traceId(),
                request.bindingRecovery());
    }

    /**
     * 解析仅用于进程日志文件名的统一认证号。
     *
     * <p>优先使用用户主数据；滚动升级或测试装配未注入用户仓储时，只允许从稳定的
     * {@code users/{统一认证号}} session 路径恢复，不能回退到平台 userId。
     */
    private String logUnifiedAuthId(OpencodeProcessStartupRequest request) {
        if (userRepository != null) {
            String value = userRepository.findByUserId(request.userId())
                    .map(User::unifiedAuthId)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .orElse(null);
            if (value != null) {
                return value;
            }
        }
        Path sessionPath = Path.of(request.sessionPath()).normalize();
        Path parent = sessionPath.getParent();
        return parent != null
                        && parent.getFileName() != null
                        && "users".equals(parent.getFileName().toString())
                        && sessionPath.getFileName() != null
                ? sessionPath.getFileName().toString().trim()
                : null;
    }

    /**
     * 合并调用方环境、目标 Java 平台引用目录和平台内部代理变量。
     *
     * <p>引用目录是可选的滚动升级能力：旧库缺少参数时不阻止既有进程启动；调用方显式提供同名值时
     * 保留调用方选择。内部代理变量继续由平台权威配置覆盖，避免调用方替换鉴权或路由信息。
     */
    private Map<String, String> startupEnvironment(OpencodeProcessStartupRequest request) {
        Map<String, String> environment = new java.util.LinkedHashMap<>(request.environment());
        if (!environment.containsKey(OPENCODE_REFERENCES_DIR_PARAM) && commonParameterValues != null) {
            commonParameterValues.resolvedValue(OPENCODE_REFERENCES_DIR_PARAM, ParameterPlatform.current())
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .ifPresent(value -> environment.put(OPENCODE_REFERENCES_DIR_PARAM, value));
        }
        if (internalProxySettings != null) {
            environment.put(InternalModelProxyRuntimeSettings.API_KEY_ENV_NAME, internalProxySettings.requireApiKey());
            environment.put(InternalModelProxyRuntimeSettings.BASE_URL_ENV_NAME, internalProxySettings.sameNodeProxyBaseUrl());
            environment.put(InternalModelProxyRuntimeSettings.UCID_ENV_NAME, unifiedAuthId(request.userId()));
        }
        return Map.copyOf(environment);
    }

    private String unifiedAuthId(com.enterprise.testagent.domain.user.UserId userId) {
        if (userRepository == null) {
            return userId.value();
        }
        return userRepository.findByUserId(userId)
                .map(User::unifiedAuthId)
                .filter(value -> value != null && !value.isBlank())
                .orElse(userId.value());
    }

    private ExecutionNode projectExecutionNode(OpencodeServerProcess process, Instant now, String traceId) {
        // 新进程表是主数据源；这里仅投影兼容节点，供既有 cancel/diff/runtime 链路按节点 ID 回查。
        return new ExecutionNode(
                new ExecutionNodeId("node_" + process.processId().value()),
                process.baseUrl(),
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                now,
                Set.of("opencode", "user-process"),
                now,
                now,
                traceId);
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static void sleepCurrentThread(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 启动健康确认被中断", Map.of(), exception);
        }
    }
}
