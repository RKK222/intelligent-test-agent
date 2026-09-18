package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigPreviewSourceResolver;
import com.enterprise.testagent.domain.configuration.RtkRuntimePolicy;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeObservabilityGenerationRepository;
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
import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityTokenService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerCommandNotDispatchedException;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(OpencodeProcessStartupService.class);
    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final String OPENCODE_REFERENCES_DIR_PARAM = "OPENCODE_REFERENCES_DIR";
    private static final String OPENCODE_APP_WORKSPACE_ROOT_PARAM = "OPENCODE_APP_WORKSPACE_ROOT";
    private static final Duration DEFAULT_STARTUP_HEALTH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STARTUP_HEALTH_POLL_INTERVAL = Duration.ofMillis(500);
    /** 旧 manager 在 start 后会立即补发心跳；最多等待约两秒读取同一 state 的启动时间。 */
    private static final int MANAGER_STATE_STARTED_AT_MAX_ATTEMPTS = 5;

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
    private PublicAgentConfigPreviewSourceResolver publicPreviewSourceResolver;
    private OpencodeProcessStopService stopService;
    private WorkspaceGitToolTokenService workspaceGitToolTokenService;
    private CodeKnowledgeToolTokenService codeKnowledgeToolTokenService;
    private OpencodeObservabilityTokenService observabilityTokenService;
    private OpencodeObservabilityGenerationRepository observabilityGenerationRepository;
    private LocalClientLifecycleGateway localClientLifecycleGateway;

    /** 启动前选择用户有效公共个人配置或共享运行副本；方法注入保持既有测试构造器兼容。 */
    @Autowired
    void setConfigLinkService(OpencodeProcessConfigLinkService configLinkService) {
        this.configLinkService = Objects.requireNonNull(configLinkService, "configLinkService must not be null");
    }

    /** 公共个人配置源由 workspace-management 校验；模块测试缺少实现时继续使用共享配置。 */
    @Autowired(required = false)
    void setPublicPreviewSourceResolver(PublicAgentConfigPreviewSourceResolver resolver) {
        this.publicPreviewSourceResolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /** Spring 生产路径复用统一停止服务执行启动竞争补偿。 */
    @Autowired
    void setStopService(OpencodeProcessStopService stopService) {
        this.stopService = Objects.requireNonNull(stopService, "stopService must not be null");
    }

    /** 启动时为公共 Tool 注入仅限工作区 Git 入口的用户签名凭据。 */
    @Autowired
    void setWorkspaceGitToolTokenService(WorkspaceGitToolTokenService workspaceGitToolTokenService) {
        this.workspaceGitToolTokenService = Objects.requireNonNull(
                workspaceGitToolTokenService, "workspaceGitToolTokenService must not be null");
    }

    /** 启动时注入与工作区 Git audience 隔离的代码知识只读凭据。 */
    @Autowired
    void setCodeKnowledgeToolTokenService(CodeKnowledgeToolTokenService codeKnowledgeToolTokenService) {
        this.codeKnowledgeToolTokenService = Objects.requireNonNull(
                codeKnowledgeToolTokenService, "codeKnowledgeToolTokenService must not be null");
    }

    /** 同一公共启动入口注入与进程代次绑定的 Observability 专用凭据。 */
    @Autowired
    void setObservabilityTokenService(OpencodeObservabilityTokenService observabilityTokenService) {
        this.observabilityTokenService = Objects.requireNonNull(
                observabilityTokenService, "observabilityTokenService must not be null");
    }

    /** 启动候选写入后冻结当前 Observability 代次，避免后续健康 traceId 将其覆盖。 */
    @Autowired
    void setObservabilityGenerationRepository(
            OpencodeObservabilityGenerationRepository observabilityGenerationRepository) {
        this.observabilityGenerationRepository = Objects.requireNonNull(
                observabilityGenerationRepository, "observabilityGenerationRepository must not be null");
    }

    /** LOCAL_CLIENT 目标仍通过本公共启动入口委托反向隧道，禁止业务层绕过。 */
    @Autowired
    void setLocalClientLifecycleGateway(LocalClientLifecycleGateway localClientLifecycleGateway) {
        this.localClientLifecycleGateway = Objects.requireNonNull(
                localClientLifecycleGateway, "localClientLifecycleGateway must not be null");
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
     * 自动启动或显式启动本地 OpenCode。启动成功必须同时具有客户端实际 PID、启动时间和 loopback health。
     */
    public LocalClientLifecycleResult startLocalClientAndVerify(
            LocalClientInstanceId clientInstanceId,
            long connectionGeneration,
            String traceId) {
        if (localClientLifecycleGateway == null) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "本地客户端生命周期网关未启用");
        }
        LocalClientLifecycleResult result = localClientLifecycleGateway.start(
                clientInstanceId, connectionGeneration, traceId);
        if (!result.success()
                || result.processStatus() != LocalClientProcessStatus.RUNNING
                || result.processId() == null
                || result.processStartedAt() == null
                || result.opencodePort() == null
                || !result.opencodeHealthy()) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "本地 OpenCode 启动后健康校验未通过");
        }
        return result;
    }

    /** 显式重启复用同一公共启动语义，并以客户端实际启动时间作为唯一权威值。 */
    public LocalClientLifecycleResult restartLocalClientAndVerify(
            LocalClientInstanceId clientInstanceId,
            long connectionGeneration,
            String traceId) {
        if (localClientLifecycleGateway == null) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "本地客户端生命周期网关未启用");
        }
        LocalClientLifecycleResult result = localClientLifecycleGateway.restart(
                clientInstanceId, connectionGeneration, traceId);
        if (!result.success()
                || result.processStatus() != LocalClientProcessStatus.RUNNING
                || result.processId() == null
                || result.processStartedAt() == null
                || result.opencodePort() == null
                || !result.opencodeHealthy()) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "本地 OpenCode 重启后健康校验未通过");
        }
        return result;
    }

    /**
     * 调用 manager start 后立即确认健康，并按可选 operation 记录启动公共链路进度。
     */
    public OpencodeServerProcess startAndVerify(
            OpencodeProcessStartupRequest request,
            OpencodeProcessStartProgress progress) {
        request = withStableProcessId(request);
        OpencodeProcessStartProgress resolvedProgress = progress == null ? OpencodeProcessStartProgress.noop() : progress;
        Optional<OpencodeServerProcess> expectedExisting = expectedExistingAssignment(request);
        try {
            resolvedProgress.step(OpencodeProcessStartOperationStep.STARTING_PROCESS);
            if (configLinkService != null) {
                preparePublicConfigLink(request);
            }
            OpencodeProcessStartCommand command = startCommand(request);
            OpencodeProcessStartResult started = gateway.startProcess(command);
            if (started == null) {
                throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程启动未返回结果");
            }
            Instant managerStartedAt = authoritativeStartedAt(request, command, started);
            OpencodeServerProcess candidate = startupCandidate(
                    request,
                    started.pid(),
                    managerStartedAt,
                    started.message());
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
     * 启动前只解析当前用户在本服务器上的一个稳定公共个人配置目录；无记录或校验失败时回退共享副本。
     *
     * <p>解析仅发生在 start/restart，不进入健康轮询；个人目录不可用不能阻断进程启动。</p>
     */
    private void preparePublicConfigLink(OpencodeProcessStartupRequest request) {
        if (!request.sharedPublicConfigRequired() && publicPreviewSourceResolver != null) {
            try {
                Optional<String> personalConfig = publicPreviewSourceResolver.resolvePublicPersonalConfigPath(
                        request.userId(),
                        request.linuxServerId().value());
                if (personalConfig.isPresent()) {
                    configLinkService.switchTo(personalConfig.get(), request.configPath());
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "event=opencode_public_preview_start_fallback linuxServerId={} userId={} exceptionType={}",
                        request.linuxServerId().value(),
                        request.userId().value(),
                        exception.getClass().getSimpleName());
            }
        }
        configLinkService.switchToShared(request.sessionPath(), request.configPath());
    }

    /**
     * 在外部 restart 已返回 STARTED 后，复用同一套候选快照、health 和最终状态回写逻辑。
     */
    public OpencodeServerProcess markStartedAndVerify(
            OpencodeProcessStartupRequest request,
            Long pid,
            Instant startedAt,
            String startMessage) {
        return markStartedAndVerify(request, pid, startedAt, startMessage, OpencodeProcessStartProgress.noop());
    }

    /**
     * 在外部 restart 已返回 STARTED 后，复用同一套候选快照、health 和最终状态回写逻辑。
     */
    public OpencodeServerProcess markStartedAndVerify(
            OpencodeProcessStartupRequest request,
            Long pid,
            Instant startedAt,
            String startMessage,
            OpencodeProcessStartProgress progress) {
        return markStartedAndVerify(
                request,
                pid,
                startedAt,
                startMessage,
                progress,
                expectedExistingAssignment(request));
    }

    private OpencodeServerProcess markStartedAndVerify(
            OpencodeProcessStartupRequest request,
            Long pid,
            Instant startedAt,
            String startMessage,
            OpencodeProcessStartProgress progress,
            Optional<OpencodeServerProcess> expectedExisting) {
        return markStartedAndVerify(
                request,
                startupCandidate(request, pid, startedAt, startMessage),
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
        if (observabilityGenerationRepository != null) {
            observabilityGenerationRepository.save(candidate.processId(), request.traceId());
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
            running = reconcileConcurrentRunningInstance(candidate, running);
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

    /**
     * 同一启动实例可能被并发状态查询提前写成 RUNNING。此时最终 CAS 虽然失败，但进程身份没有变化，
     * 启动程序应幂等完成绑定收口；PID、manager 启动时间或运行坐标任一变化仍按真实冲突处理。
     */
    private OpencodeServerProcess reconcileConcurrentRunningInstance(
            OpencodeServerProcess candidate,
            OpencodeServerProcess verifiedRunning) {
        Optional<OpencodeServerProcess> current = repository.findOpencodeServerProcessById(candidate.processId());
        if (current.isPresent() && sameStartedInstance(current.get(), verifiedRunning)) {
            OpencodeServerProcess reconciled = current.get();
            LOGGER.info(
                    "event=opencode_startup_idempotent_reconcile processId={} linuxServerId={} containerId={} port={} pid={}",
                    reconciled.processId().value(),
                    reconciled.linuxServerId().value(),
                    reconciled.containerId().value(),
                    reconciled.port(),
                    reconciled.pid());
            return reconciled;
        }
        throw new OpencodeProcessAssignmentConflictException("TestAgent 进程分配已变化，拒绝旧健康结果回写");
    }

    private boolean sameStartedInstance(
            OpencodeServerProcess current,
            OpencodeServerProcess verifiedRunning) {
        return current.status() == OpencodeServerProcessStatus.RUNNING
                && current.processId().equals(verifiedRunning.processId())
                && current.userId().equals(verifiedRunning.userId())
                && current.linuxServerId().equals(verifiedRunning.linuxServerId())
                && current.containerId().equals(verifiedRunning.containerId())
                && current.port() == verifiedRunning.port()
                && Objects.equals(current.pid(), verifiedRunning.pid())
                && Objects.equals(current.startedAt(), verifiedRunning.startedAt())
                && current.sessionPath().equals(verifiedRunning.sessionPath())
                && current.configPath().equals(verifiedRunning.configPath())
                && current.createdAt().equals(verifiedRunning.createdAt());
    }

    private OpencodeServerProcess startupCandidate(
            OpencodeProcessStartupRequest request,
            Long pid,
            Instant managerStartedAt,
            String startMessage) {
        Instant now = Instant.now(clock);
        if (managerStartedAt == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "TestAgent 管理进程启动结果缺少权威启动时间");
        }
        // PostgreSQL timestamp 仅保留微秒；先对 manager 权威值截断，避免落库后与心跳纳秒值比较漂移。
        Instant startedAt = managerStartedAt.truncatedTo(ChronoUnit.MICROS);
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
                startedAt,
                now,
                startMessage == null || startMessage.isBlank() ? "started" : startMessage,
                createdAt,
                now,
                request.traceId());
    }

    /**
     * 新 manager 直接在命令结果返回 state.startedAt；滚动升级期间旧 manager 缺字段时，等待其紧随
     * STARTED 结果发送的即时心跳，并按完整运行坐标读取同一份 state。任何路径都不使用 Java 观察时间。
     */
    private Instant authoritativeStartedAt(
            OpencodeProcessStartupRequest request,
            OpencodeProcessStartCommand command,
            OpencodeProcessStartResult started) {
        if (started.startedAt() != null) {
            return started.startedAt();
        }
        if (started.pid() == null || started.pid() < 1) {
            throw missingAuthoritativeStartedAt();
        }
        for (int attempt = 1; attempt <= MANAGER_STATE_STARTED_AT_MAX_ATTEMPTS; attempt++) {
            Optional<Instant> heartbeatStartedAt = managerStateStartedAt(request, command, started.pid());
            if (heartbeatStartedAt.isPresent()) {
                return heartbeatStartedAt.get();
            }
            if (attempt < MANAGER_STATE_STARTED_AT_MAX_ATTEMPTS) {
                startupHealthSleeper.accept(startupHealthPollInterval);
            }
        }
        throw missingAuthoritativeStartedAt();
    }

    private Optional<Instant> managerStateStartedAt(
            OpencodeProcessStartupRequest request,
            OpencodeProcessStartCommand command,
            Long pid) {
        return heartbeatStore.liveManagerSnapshots().stream()
                .filter(snapshot -> snapshot.container().linuxServerId().equals(request.linuxServerId()))
                .filter(snapshot -> snapshot.container().containerId().equals(request.containerId()))
                .flatMap(snapshot -> snapshot.managedProcesses().stream())
                .filter(process -> matchesManagerState(request, command, pid, process))
                .map(ManagedOpencodeProcessSnapshot::startedAt)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private boolean matchesManagerState(
            OpencodeProcessStartupRequest request,
            OpencodeProcessStartCommand command,
            Long pid,
            ManagedOpencodeProcessSnapshot process) {
        return process.port() == request.port()
                && Objects.equals(process.pid(), pid)
                && Objects.equals(process.unifiedAuthId(), command.unifiedAuthId())
                && Objects.equals(process.sessionPath(), request.sessionPath())
                && Objects.equals(process.configPath(), request.configPath());
    }

    private PlatformException missingAuthoritativeStartedAt() {
        return new PlatformException(
                ErrorCode.OPENCODE_BAD_GATEWAY,
                "TestAgent 管理进程启动结果和实时 state 均缺少权威启动时间");
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
     * 合并调用方环境、目标 Java 平台只读引用目录和平台内部代理变量。
     *
     * <p>引用目录是可选的滚动升级能力：旧库缺少参数时不阻止既有进程启动；调用方显式提供同名值时
     * 保留调用方选择。内部代理变量继续由平台权威配置覆盖，避免调用方替换鉴权或路由信息。
     */
    private Map<String, String> startupEnvironment(OpencodeProcessStartupRequest request) {
        Map<String, String> environment = new java.util.LinkedHashMap<>(request.environment());
        // RTK 是平台策略能力，调用方传入的同名环境变量不能覆盖数据库中的全局开关。
        environment.put("TEST_AGENT_RTK_ENABLED", Boolean.toString(RtkRuntimePolicy.enabled(commonParameterValues)));
        injectOptionalPathParameter(environment, OPENCODE_REFERENCES_DIR_PARAM);
        // 新引用统一使用 OPENCODE_REFERENCES_DIR；旧根参数仅保留给历史 JSONC 的滚动兼容。
        injectOptionalPathParameter(environment, OPENCODE_APP_WORKSPACE_ROOT_PARAM);
        if (internalProxySettings != null) {
            environment.put(InternalModelProxyRuntimeSettings.API_KEY_ENV_NAME, internalProxySettings.requireApiKey());
            environment.put(InternalModelProxyRuntimeSettings.BASE_URL_ENV_NAME, internalProxySettings.sameNodeProxyBaseUrl());
            environment.put(InternalModelProxyRuntimeSettings.UCID_ENV_NAME, unifiedAuthId(request.userId()));
        }
        if (internalProxySettings != null
                && (workspaceGitToolTokenService != null || codeKnowledgeToolTokenService != null)) {
            environment.put(
                    WorkspaceGitToolTokenService.BASE_URL_ENV_NAME,
                    internalProxySettings.sameNodeBaseUrl());
        }
        if (workspaceGitToolTokenService != null && internalProxySettings != null) {
            environment.put(
                    WorkspaceGitToolTokenService.TOKEN_ENV_NAME,
                    workspaceGitToolTokenService.issue(request.userId()));
        }
        if (codeKnowledgeToolTokenService != null && internalProxySettings != null) {
            environment.put(
                    CodeKnowledgeToolTokenService.TOKEN_ENV_NAME,
                    codeKnowledgeToolTokenService.issue(request.userId()));
        }
        if (observabilityTokenService != null && internalProxySettings != null) {
            environment.put(
                    OpencodeObservabilityTokenService.BASE_URL_ENV_NAME,
                    internalProxySettings.sameNodeBaseUrl());
            environment.put(
                    OpencodeObservabilityTokenService.TOKEN_ENV_NAME,
                    observabilityTokenService.issue(request));
            environment.put(OpencodeObservabilityTokenService.RUNTIME_KIND_ENV_NAME, "SERVER_PROCESS");
            environment.put(OpencodeObservabilityTokenService.GENERATION_ENV_NAME, request.traceId());
            environment.put(OpencodeObservabilityTokenService.PROCESS_ID_ENV_NAME, request.processId().value());
            environment.put(OpencodeObservabilityTokenService.SERVER_ID_ENV_NAME, request.linuxServerId().value());
        }
        return Map.copyOf(environment);
    }

    /** 首次分配也在 manager start 前生成稳定进程 ID，使专用凭据与最终持久化身份完全一致。 */
    private OpencodeProcessStartupRequest withStableProcessId(OpencodeProcessStartupRequest request) {
        if (request.processId() != null) {
            return request;
        }
        return new OpencodeProcessStartupRequest(
                request.userId(),
                new OpencodeProcessId(RuntimeIdGenerator.opencodeProcessId()),
                request.createdAt(),
                request.bindingCreatedAt(),
                request.linuxServerId(),
                request.containerId(),
                request.port(),
                request.baseUrl(),
                request.sessionPath(),
                request.configPath(),
                request.environment(),
                request.traceId(),
                request.bindingRecovery(),
                request.sharedPublicConfigRequired());
    }

    private void injectOptionalPathParameter(Map<String, String> environment, String parameterName) {
        if (environment.containsKey(parameterName)) {
            environment.computeIfPresent(parameterName, (ignored, value) -> normalizedEnvironmentPath(value));
            return;
        }
        if (commonParameterValues == null) {
            return;
        }
        commonParameterValues.resolvedValue(parameterName, ParameterPlatform.current())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::normalizedEnvironmentPath)
                .ifPresent(value -> environment.put(parameterName, value));
    }

    /**
     * OpenCode 会把环境变量值直接插入 references 与权限 glob；统一去除尾部分隔符，避免双斜线导致
     * 引用能定位文件但精确 external_directory allow 无法命中规范化后的真实路径。
     */
    private String normalizedEnvironmentPath(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            return trimmed;
        }
        return java.nio.file.Path.of(trimmed).normalize().toString();
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
