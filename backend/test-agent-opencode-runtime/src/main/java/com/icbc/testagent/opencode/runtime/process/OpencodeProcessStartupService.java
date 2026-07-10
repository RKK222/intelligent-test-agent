package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStep;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.run.ConversationContextStore;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.opencode.runtime.internalmodel.InternalModelProxyRuntimeSettings;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
    private static final Duration DEFAULT_STARTUP_HEALTH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STARTUP_HEALTH_POLL_INTERVAL = Duration.ofMillis(500);

    private final OpencodeProcessManagementRepository repository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final OpencodeProcessManagerGateway gateway;
    private final OpencodeProcessStatusQueryService statusQueryService;
    private final Clock clock;
    private final Duration startupHealthTimeout;
    private final Duration startupHealthPollInterval;
    private final Consumer<Duration> startupHealthSleeper;
    private final InternalModelProxyRuntimeSettings internalProxySettings;
    private final UserRepository userRepository;
    private final ConversationContextStore conversationContextStore;

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
            ManagerControlSettings managerControlSettings,
            InternalModelProxyRuntimeSettings internalProxySettings,
            UserRepository userRepository,
            ConversationContextStore conversationContextStore) {
        this(
                repository,
                executionNodeRepository,
                gateway,
                heartbeatStore,
                statusQueryService,
                Clock.systemUTC(),
                managerControlSettings.commandTimeout(),
                DEFAULT_STARTUP_HEALTH_POLL_INTERVAL,
                OpencodeProcessStartupService::sleepCurrentThread,
                internalProxySettings,
                userRepository,
                conversationContextStore);
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
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.statusQueryService = statusQueryService == null
                ? new OpencodeProcessStatusQueryService(repository, gateway, heartbeatStore, clock)
                : statusQueryService;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.startupHealthTimeout = positive(startupHealthTimeout, DEFAULT_STARTUP_HEALTH_TIMEOUT);
        this.startupHealthPollInterval = positive(startupHealthPollInterval, DEFAULT_STARTUP_HEALTH_POLL_INTERVAL);
        this.startupHealthSleeper = Objects.requireNonNull(startupHealthSleeper, "startupHealthSleeper must not be null");
        this.internalProxySettings = internalProxySettings;
        this.userRepository = userRepository;
        this.conversationContextStore = conversationContextStore;
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
        try {
            resolvedProgress.step(OpencodeProcessStartOperationStep.STARTING_PROCESS);
            OpencodeProcessStartResult started = gateway.startProcess(startCommand(request));
            if (started == null) {
                throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 管理进程启动未返回结果");
            }
            return markStartedAndVerify(request, started.pid(), started.message(), resolvedProgress);
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
        OpencodeProcessStartProgress resolvedProgress = progress == null ? OpencodeProcessStartProgress.noop() : progress;
        Instant now = Instant.now(clock);
        OpencodeProcessId processId = request.processId() == null
                ? new OpencodeProcessId(RuntimeIdGenerator.opencodeProcessId())
                : request.processId();
        Instant createdAt = request.createdAt() == null ? now : request.createdAt();
        OpencodeServerProcess candidate = new OpencodeServerProcess(
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
        resolvedProgress.step(OpencodeProcessStartOperationStep.SAVING_CANDIDATE);
        repository.saveOpencodeServerProcess(candidate);
        OpencodeProcessStatusProbe probe = waitForStartupHealth(candidate.processId(), request.traceId(), resolvedProgress);
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
        if (conversationContextStore != null) {
            conversationContextStore.invalidateProcess(running.processId().value());
        }
        resolvedProgress.step(OpencodeProcessStartOperationStep.SAVING_BINDING);
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
        executionNodeRepository.save(projectExecutionNode(running, running.updatedAt(), request.traceId()));
        return running;
    }

    private OpencodeProcessStatusProbe waitForStartupHealth(OpencodeProcessId processId, String traceId) {
        return waitForStartupHealth(processId, traceId, OpencodeProcessStartProgress.noop());
    }

    private OpencodeProcessStatusProbe waitForStartupHealth(
            OpencodeProcessId processId,
            String traceId,
            OpencodeProcessStartProgress progress) {
        int maxAttempts = startupHealthMaxAttempts();
        Instant deadline = Instant.now(clock).plus(startupHealthTimeout);
        OpencodeProcessStatusProbe probe = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            progress.step(OpencodeProcessStartOperationStep.CHECKING_PROCESS);
            probe = statusQueryService.query(processId, traceId);
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
        if (probe.status() == OpencodeProcessProbeStatus.NOT_STARTED) {
            return;
        }
        OpencodeServerProcess current = probe.process().orElse(candidate);
        OpencodeServerProcessStatus status =
                probe.errorCode() == null || probe.errorCode() == ErrorCode.OPENCODE_UNAVAILABLE
                        ? OpencodeServerProcessStatus.UNHEALTHY
                        : OpencodeServerProcessStatus.FAILED;
        Instant checkedAt = probe.checkedAt();
        repository.saveOpencodeServerProcess(new OpencodeServerProcess(
                current.processId(),
                current.userId(),
                current.linuxServerId(),
                current.containerId(),
                current.port(),
                current.pid(),
                current.baseUrl(),
                status,
                current.sessionPath(),
                current.configPath(),
                current.startedAt(),
                checkedAt,
                probe.message(),
                current.createdAt(),
                checkedAt,
                traceId));
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
        String message = probe.message() == null || probe.message().isBlank() ? "opencode 健康检测异常" : probe.message();
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
                request.linuxServerId(),
                request.containerId(),
                request.port(),
                request.baseUrl(),
                request.sessionPath(),
                request.configPath(),
                internalProxyEnvironment(request),
                request.traceId());
    }

    private Map<String, String> internalProxyEnvironment(OpencodeProcessStartupRequest request) {
        if (internalProxySettings == null) {
            return request.environment();
        }
        Map<String, String> environment = new java.util.LinkedHashMap<>(request.environment());
        environment.put(InternalModelProxyRuntimeSettings.API_KEY_ENV_NAME, internalProxySettings.requireApiKey());
        environment.put(InternalModelProxyRuntimeSettings.BASE_URL_ENV_NAME, internalProxySettings.sameNodeProxyBaseUrl());
        environment.put(InternalModelProxyRuntimeSettings.UCID_ENV_NAME, unifiedAuthId(request.userId()));
        return Map.copyOf(environment);
    }

    private String unifiedAuthId(com.icbc.testagent.domain.user.UserId userId) {
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
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "opencode 启动健康确认被中断", Map.of(), exception);
        }
    }
}
