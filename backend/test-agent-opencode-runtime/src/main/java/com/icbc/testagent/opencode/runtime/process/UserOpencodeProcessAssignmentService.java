package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessStartOperation;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessStartOperationRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStep;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户 opencode 进程分配服务，负责状态检查、初始化和 Run 目标节点投影。
 */
@Service
public class UserOpencodeProcessAssignmentService {

    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final int CONTAINER_CANDIDATE_LIMIT = 100;
    private static final Duration STALE_READY_GRACE_PERIOD = Duration.ofSeconds(60);
    private static final String PARAM_OPENCODE_SESSION_DIR = "OPENCODE_SESSION_DIR";
    private static final String PARAM_OPENCODE_PUBLIC_CONFIG_DIR = "OPENCODE_PUBLIC_CONFIG_DIR";
    private static final Pattern OPERATION_ID_PATTERN = Pattern.compile("^opi_[A-Za-z0-9_-]{8,120}$");
    private static final CommonParameterValues EMPTY_PARAMETER_VALUES = new CommonParameterValues() {
        @Override
        public java.util.Optional<String> resolvedValue(String englishName) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<String> resolvedValue(String englishName, com.icbc.testagent.domain.configuration.ParameterPlatform platform) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<com.icbc.testagent.domain.configuration.CommonParameter> raw(
                String englishName, com.icbc.testagent.domain.configuration.ParameterPlatform platform) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<com.icbc.testagent.domain.configuration.CommonParameter> findAll() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.icbc.testagent.domain.configuration.ResolvedParameter> resolvedAll() {
            return java.util.List.of();
        }
    };

    private final OpencodeProcessManagementRepository repository;
    private final CommonParameterValues commonParameterValues;
    private final ExecutionNodeRepository executionNodeRepository;
    private final OpencodeProcessManagerGateway gateway;
    private final BackendJavaProcessLifecycleService backendLifecycle;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final OpencodeProcessStartupService startupService;
    private final OpencodeProcessStatusQueryService statusQueryService;
    private final OpencodeServerAddressResolver addressResolver;
    private final OpencodeProcessStartOperationRepository startOperationRepository;

    /**
     * 注入进程管理 Repository、兼容节点 Repository 和管理进程 gateway。
     */
    public UserOpencodeProcessAssignmentService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            BackendJavaProcessLifecycleService backendLifecycle) {
        this(repository, executionNodeRepository, gateway, backendLifecycle, disabledHeartbeatStore());
    }

    /**
     * 注入进程管理 Repository、兼容节点 Repository、管理进程 gateway 和运行进程心跳端口。
     */
    public UserOpencodeProcessAssignmentService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            BackendJavaProcessLifecycleService backendLifecycle,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        this(repository, EMPTY_PARAMETER_VALUES, executionNodeRepository, gateway, backendLifecycle, heartbeatStore, null, null, null);
    }

    /**
     * 测试构造器：允许注入通用参数仓库（工作区/session 路径参数从 common_parameters 读取）。
     */
    UserOpencodeProcessAssignmentService(
            OpencodeProcessManagementRepository repository,
            CommonParameterValues commonParameterValues,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            BackendJavaProcessLifecycleService backendLifecycle) {
        this(repository, commonParameterValues, executionNodeRepository, gateway, backendLifecycle, disabledHeartbeatStore());
    }

    /**
     * 注入完整依赖；Spring 容器默认走这个构造器。
     */
    @Autowired
    public UserOpencodeProcessAssignmentService(
            OpencodeProcessManagementRepository repository,
            CommonParameterValues commonParameterValues,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            BackendJavaProcessLifecycleService backendLifecycle,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStartupService startupService,
            OpencodeProcessStatusQueryService statusQueryService,
            OpencodeProcessStartOperationRepository startOperationRepository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.backendLifecycle = Objects.requireNonNull(backendLifecycle, "backendLifecycle must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.statusQueryService = statusQueryService == null
                ? new OpencodeProcessStatusQueryService(repository, gateway, heartbeatStore)
                : statusQueryService;
        this.addressResolver = new OpencodeServerAddressResolver(backendLifecycle.advertisedHost());
        this.startOperationRepository = startOperationRepository;
        this.startupService = startupService == null
                ? new OpencodeProcessStartupService(
                        repository,
                        executionNodeRepository,
                        gateway,
                        heartbeatStore,
                        this.statusQueryService,
                        Clock.systemUTC())
                : startupService;
    }

    /**
     * 兼容旧测试构造器，生产路径使用注入的公共启动服务。
     */
    public UserOpencodeProcessAssignmentService(
            OpencodeProcessManagementRepository repository,
            CommonParameterValues commonParameterValues,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            BackendJavaProcessLifecycleService backendLifecycle,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        this(
                repository,
                commonParameterValues,
                executionNodeRepository,
                gateway,
                backendLifecycle,
                heartbeatStore == null ? disabledHeartbeatStore() : heartbeatStore,
                null,
                null,
                null);
    }

    /**
     * 兼容旧测试构造器，允许注入进程启动进度仓储。
     */
    public UserOpencodeProcessAssignmentService(
            OpencodeProcessManagementRepository repository,
            CommonParameterValues commonParameterValues,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            BackendJavaProcessLifecycleService backendLifecycle,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStartOperationRepository startOperationRepository) {
        this(
                repository,
                commonParameterValues,
                executionNodeRepository,
                gateway,
                backendLifecycle,
                heartbeatStore == null ? disabledHeartbeatStore() : heartbeatStore,
                null,
                null,
                startOperationRepository);
    }

    /**
     * 查询当前用户 opencode 进程状态；不自动启动进程。
     */
    public UserOpencodeProcessStatusResponse status(UserId userId, String agentId, String traceId) {
        validateAgent(agentId);
        Instant now = Instant.now();
        Optional<UserOpencodeProcessBinding> binding = repository.findUserBinding(userId, OPENCODE_AGENT_ID)
                .filter(item -> item.status() == UserOpencodeProcessBindingStatus.ACTIVE);
        if (binding.isEmpty()) {
            return hasInitializableContainer()
                    ? needsInitialization("需要初始化 opencode 进程", now)
                    : unavailable("没有可用的 opencode 容器", now);
        }
        Optional<OpencodeServerProcess> process = boundProcess(binding.get());
        if (process.isEmpty()) {
            return canRebuildOn(binding.get().linuxServerId())
                    ? needsInitialization("opencode 进程不可用，需要重新初始化", binding.get(), now)
                    : unavailable("原 Linux 服务器没有可用的 opencode 容器", binding.get(), now);
        }
        OpencodeServerProcess current = process.get();
        OpencodeProcessStatusProbe probe = statusQueryService.query(current.processId(), traceId);
        if (probe.status() == OpencodeProcessProbeStatus.RUNNING) {
            OpencodeServerProcess refreshed = probe.process().orElse(current);
            return ready(refreshed, "opencode 进程可用", now);
        }
        OpencodeServerProcess refreshed = probe.process().orElse(current);
        // STALE 只在最后一次成功健康检查后的短暂宽限期内保留可用状态。
        if (probe.status() == OpencodeProcessProbeStatus.STALE) {
            if (isWithinStaleReadyGrace(current, probe.checkedAt())) {
                return ready(refreshed, "状态暂时无法确认：" + probe.message(), now);
            }
            return canRebuildOn(binding.get().linuxServerId())
                    ? needsInitialization(statusFailureMessage(probe), refreshed, now)
                    : unavailable("opencode 进程健康状态暂无法确认：" + probe.message(), refreshed, now);
        }
        if (probe.errorCode() != null) {
            // 有错误码但非 STALE，可能是明确的失败，根据上次状态决定
            if (current.status() == OpencodeServerProcessStatus.RUNNING) {
                return ready(refreshed, "状态暂时无法确认：" + probe.message(), now);
            }
            return unavailable("opencode 进程健康状态暂无法确认：" + probe.message(), refreshed, now);
        }
        return canRebuildOn(binding.get().linuxServerId())
                ? needsInitialization(statusFailureMessage(probe), refreshed, now)
                : unavailable(statusFailureMessage(probe).replace("需要重新初始化", "且当前没有可用容器"), refreshed, now);
    }

    /**
     * 文件 WebSocket 路由只需要知道用户进程所属服务器，不应触发 manager health/start 命令。
     *
     * <p>返回 READY 仅表示存在可用于服务器归属判断的 ACTIVE binding 与可恢复进程记录；
     * Run、初始化、头像状态等链路仍必须调用 {@link #status(UserId, String, String)}
     * 或 {@link #requireReadyProcess(UserId, String, String)} 做强健康检查。
     */
    public UserOpencodeProcessFileRoutingAffinity fileRoutingAffinity(UserId userId, String agentId, String traceId) {
        validateAgent(agentId);
        Instant now = Instant.now();
        Optional<UserOpencodeProcessBinding> binding = repository.findUserBinding(userId, OPENCODE_AGENT_ID)
                .filter(item -> item.status() == UserOpencodeProcessBindingStatus.ACTIVE);
        if (binding.isEmpty()) {
            return hasInitializableContainer()
                    ? needsInitializationAffinity("需要初始化 opencode 进程", now)
                    : unavailableAffinity("没有可用的 opencode 容器", now);
        }
        Optional<OpencodeServerProcess> process = activeProcess(binding.get());
        if (process.isEmpty()) {
            return canRebuildOn(binding.get().linuxServerId())
                    ? needsInitializationAffinity("opencode 进程不可用，需要重新初始化", binding.get(), now)
                    : unavailableAffinity("原 Linux 服务器没有可用的 opencode 容器", binding.get(), now);
        }
        return readyAffinity(process.get(), "文件路由服务器归属可用", now);
    }

    /**
     * API 层后端路由只需要 ACTIVE binding 的服务器归属，不触发健康检查或初始化。
     *
     * <p>返回值为远端路由候选，调用方需自行与当前 Java 所在服务器比较。不存在 binding
     * 时必须走本机正常初始化/状态流程，由当前 Java 选择本机可用 manager。
     */
    public Optional<String> routingLinuxServerId(UserId userId, String agentId) {
        validateAgent(agentId);
        return repository.findUserBinding(userId, OPENCODE_AGENT_ID)
                .filter(item -> item.status() == UserOpencodeProcessBindingStatus.ACTIVE)
                .map(UserOpencodeProcessBinding::linuxServerId)
                .map(LinuxServerId::value);
    }

    /**
     * 只读取数据库 ACTIVE binding 来表达“是否已分配”，不触发 manager 健康检查、
     * 容器可用性查询或进程启动，用于跨后端状态查询失败时保留用户分配事实。
     */
    public UserOpencodeProcessStatusResponse allocationStatus(
            UserId userId,
            String agentId,
            String allocatedMessage,
            String traceId) {
        validateAgent(agentId);
        Instant now = Instant.now();
        Optional<UserOpencodeProcessBinding> binding = repository.findUserBinding(userId, OPENCODE_AGENT_ID)
                .filter(item -> item.status() == UserOpencodeProcessBindingStatus.ACTIVE);
        return binding
                .map(item -> unavailable(
                        allocatedMessage == null || allocatedMessage.isBlank()
                                ? "已分配 opencode 专属进程，但暂无法确认进程健康状态"
                                : allocatedMessage,
                        item,
                        now))
                .orElseGet(() -> unavailable("当前用户尚未分配 opencode 专属进程", now));
    }

    /**
     * 初始化或重建当前用户 opencode 进程；真实启动由 gateway 完成。
     */
    public UserOpencodeProcessStatusResponse initialize(UserId userId, String agentId, String traceId) {
        return initialize(userId, agentId, traceId, null);
    }

    /**
     * 初始化或重建当前用户 opencode 进程；operationId 存在时同步记录公共启动链路进度。
     */
    public UserOpencodeProcessStatusResponse initialize(
            UserId userId,
            String agentId,
            String traceId,
            String operationId) {
        validateAgent(agentId);
        String normalizedOperationId = normalizeOperationId(operationId);
        OpencodeProcessStartProgress progress = OpencodeProcessStartProgress.start(
                startOperationRepository,
                normalizedOperationId,
                userId,
                OPENCODE_AGENT_ID,
                traceId,
                Instant::now);
        try {
            return initializeWithProgress(userId, traceId, progress);
        } catch (PlatformException exception) {
            progress.failed(exception);
            throw exception;
        } catch (RuntimeException exception) {
            progress.failed(exception);
            throw exception;
        }
    }

    private UserOpencodeProcessStatusResponse initializeWithProgress(
            UserId userId,
            String traceId,
            OpencodeProcessStartProgress progress) {
        Instant now = Instant.now();
        progress.step(OpencodeProcessStartOperationStep.CHECKING_ASSIGNMENT);
        Optional<UserOpencodeProcessBinding> existingBinding = repository.findUserBinding(userId, OPENCODE_AGENT_ID);
        Optional<OpencodeServerProcess> existingProcess = existingBinding.flatMap(binding -> activeProcess(binding).or(() ->
                repository.findOpencodeServerProcessById(binding.processId())));
        if (existingProcess.isPresent()) {
            progress.step(OpencodeProcessStartOperationStep.CHECKING_PROCESS);
            OpencodeProcessStatusProbe probe = statusQueryService.query(existingProcess.get().processId(), traceId);
            if (probe.status() != OpencodeProcessProbeStatus.NOT_STARTED) {
                progress.step(OpencodeProcessStartOperationStep.HEALTH_CHECKING);
            }
            if (probe.status() == OpencodeProcessProbeStatus.RUNNING) {
                OpencodeServerProcess refreshed = probe.process().orElse(existingProcess.get());
                ExecutionNode node = projectExecutionNode(refreshed, now, traceId);
                executionNodeRepository.save(node);
                progress.succeeded(refreshed.processId().value(), serviceAddress(refreshed));
                return ready(refreshed, "opencode 进程可用", now);
            }
        }

        // 旧 binding 存在时只能按原 linux_server_id 查容器。跨服务器请求由 API 层
        // 路由到 binding 所属后端，避免当前 Java 静默迁移用户进程归属。
        List<OpencodeContainer> candidates = existingBinding
                .map(binding -> repository.findHealthyContainersConnectedToBackendByLinuxServer(
                        backendLifecycle.backendProcessId(),
                        binding.linuxServerId(),
                        CONTAINER_CANDIDATE_LIMIT))
                .orElseGet(() -> repository.findHealthyContainersConnectedToBackend(
                        backendLifecycle.backendProcessId(),
                        CONTAINER_CANDIDATE_LIMIT));
        progress.step(OpencodeProcessStartOperationStep.SELECTING_CONTAINER);
        OpencodeContainer container = chooseContainer(candidates);
        progress.step(OpencodeProcessStartOperationStep.PREPARING_STARTUP);
        OpencodeProcessStartCommand command = startCommand(userId, container, traceId);
        OpencodeServerProcess process = startupService.startAndVerify(new OpencodeProcessStartupRequest(
                userId,
                existingBinding.map(UserOpencodeProcessBinding::processId).orElse(null),
                existingProcess.map(OpencodeServerProcess::createdAt).orElse(null),
                existingBinding.map(UserOpencodeProcessBinding::createdAt).orElse(null),
                command.linuxServerId(),
                command.containerId(),
                command.port(),
                command.baseUrl(),
                command.sessionPath(),
                command.configPath(),
                traceId), progress);
        progress.succeeded(process.processId().value(), serviceAddress(process));
        return ready(process, "opencode 进程可用", now);
    }

    /**
     * 只读当前用户发起的初始化 operation，不触发 manager health/start 或 RunEvent。
     */
    public Optional<OpencodeProcessStartOperation> findStartOperation(
            UserId userId,
            String agentId,
            String operationId) {
        validateAgent(agentId);
        String normalizedOperationId = normalizeOperationId(operationId);
        if (startOperationRepository == null || normalizedOperationId == null) {
            return Optional.empty();
        }
        return startOperationRepository.findById(normalizedOperationId, userId);
    }

    /**
     * Run 启动前解析用户专属运行目标；缺失或不健康时要求前端先初始化。
     * STALE 状态时：如果数据库状态是 RUNNING，允许运行（使用上次成功数据）；
     * 否则要求重新初始化。
     */
    public UserOpencodeProcessAssignment requireReadyProcess(UserId userId, String agentId, String traceId) {
        validateAgent(agentId);
        Instant now = Instant.now();
        UserOpencodeProcessBinding binding = repository.findUserBinding(userId, OPENCODE_AGENT_ID)
                .filter(item -> item.status() == UserOpencodeProcessBindingStatus.ACTIVE)
                .orElseThrow(() -> unavailableException("请先初始化 opencode 进程"));
        OpencodeServerProcess process = repository.findOpencodeServerProcessById(binding.processId())
                .orElseThrow(() -> unavailableException("请先初始化 opencode 进程"));
        OpencodeProcessStatusProbe probe = statusQueryService.query(process.processId(), traceId);
        if (probe.status() == OpencodeProcessProbeStatus.RUNNING) {
            OpencodeServerProcess refreshed = probe.process().orElse(process);
            ExecutionNode node = projectExecutionNode(refreshed, now, traceId);
            executionNodeRepository.save(node);
            return new UserOpencodeProcessAssignment(node, refreshed.linuxServerId().value());
        }
        // STALE 只在最后一次成功健康检查后的短暂宽限期内放行。
        if (probe.status() == OpencodeProcessProbeStatus.STALE
                && isWithinStaleReadyGrace(process, probe.checkedAt())) {
            ExecutionNode node = projectExecutionNode(process, now, traceId);
            executionNodeRepository.save(node);
            return new UserOpencodeProcessAssignment(node, process.linuxServerId().value());
        }
        throw unavailableException("opencode 进程不可用，请先初始化");
    }

    private Optional<OpencodeServerProcess> activeProcess(UserOpencodeProcessBinding binding) {
        if (binding.status() != UserOpencodeProcessBindingStatus.ACTIVE) {
            return Optional.empty();
        }
        return repository.findOpencodeServerProcessById(binding.processId())
                .filter(this::isRecoverableProcess);
    }

    private Optional<OpencodeServerProcess> boundProcess(UserOpencodeProcessBinding binding) {
        if (binding.status() != UserOpencodeProcessBindingStatus.ACTIVE) {
            return Optional.empty();
        }
        return repository.findOpencodeServerProcessById(binding.processId());
    }

    private boolean isRecoverableProcess(OpencodeServerProcess process) {
        return process.status() == OpencodeServerProcessStatus.RUNNING
                || process.status() == OpencodeServerProcessStatus.STARTING
                || process.status() == OpencodeServerProcessStatus.UNHEALTHY;
    }

    private boolean isWithinStaleReadyGrace(OpencodeServerProcess process, Instant checkedAt) {
        if (process.status() != OpencodeServerProcessStatus.RUNNING
                || process.lastHealthCheckAt() == null
                || checkedAt == null) {
            return false;
        }
        return !process.lastHealthCheckAt().plus(STALE_READY_GRACE_PERIOD).isBefore(checkedAt);
    }

    private boolean hasInitializableContainer() {
        return !repository.findHealthyContainersConnectedToBackend(backendLifecycle.backendProcessId(), 1).isEmpty();
    }

    private boolean canRebuildOn(LinuxServerId linuxServerId) {
        return !repository.findHealthyContainersConnectedToBackendByLinuxServer(
                backendLifecycle.backendProcessId(),
                linuxServerId,
                1).isEmpty();
    }

    private OpencodeContainer chooseContainer(List<OpencodeContainer> candidates) {
        return candidates.stream()
                .filter(OpencodeContainer::canAcceptProcess)
                .sorted(Comparator
                        .comparingInt(OpencodeContainer::currentProcesses)
                        .thenComparing(container -> container.containerId().value()))
                .filter(container -> firstAvailablePort(container).isPresent())
                .findFirst()
                .orElseThrow(() -> unavailableException("没有可用的 opencode 容器或端口"));
    }

    private OpencodeProcessStartCommand startCommand(
            UserId userId,
            OpencodeContainer container,
            String traceId) {
        int port = firstAvailablePort(container)
                .orElseThrow(() -> unavailableException("没有可用的 opencode 端口"));
        String baseUrl = addressResolver.baseUrl(port);
        return new OpencodeProcessStartCommand(
                userId,
                container.linuxServerId(),
                container.containerId(),
                port,
                baseUrl,
                sessionPath(port),
                configPath(),
                traceId);
    }

    private Optional<Integer> firstAvailablePort(OpencodeContainer container) {
        Set<Integer> occupied = new HashSet<>(repository.findOccupiedPorts(container.linuxServerId(), container.containerId()));
        for (int port = container.portStart(); port <= container.portEnd(); port++) {
            if (!occupied.contains(port)) {
                return Optional.of(port);
            }
        }
        return Optional.empty();
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

    private String sessionPath(int port) {
        return ensureTrailingSlash(configuredParameter(PARAM_OPENCODE_SESSION_DIR)) + port;
    }

    private String configPath() {
        return ensureTrailingSlash(configuredParameter(PARAM_OPENCODE_PUBLIC_CONFIG_DIR));
    }

    /**
     * 从通用参数数据库读取必填参数（已展开变量引用），缺失或空白时抛异常。
     * common_parameters 为唯一事实源，不在 yaml/代码常量预留 fallback。
     */
    private String configuredParameter(String englishName) {
        return commonParameterValues.resolvedValue(englishName)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "通用参数未配置：" + englishName,
                        Map.of("parameter", englishName)));
    }

    private String ensureTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "/" : value.trim().replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private UserOpencodeProcessStatusResponse ready(OpencodeServerProcess process, String message, Instant checkedAt) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.READY,
                false,
                message,
                process.processId().value(),
                process.linuxServerId().value(),
                process.containerId().value(),
                process.port(),
                process.baseUrl(),
                checkedAt,
                UserOpencodeServiceStatus.RUNNING,
                serviceAddress(process));
    }

    private UserOpencodeProcessFileRoutingAffinity readyAffinity(
            OpencodeServerProcess process,
            String message,
            Instant checkedAt) {
        return new UserOpencodeProcessFileRoutingAffinity(
                UserOpencodeProcessAvailability.READY,
                false,
                message,
                process.processId().value(),
                process.linuxServerId().value(),
                process.containerId().value(),
                process.port(),
                serviceAddress(process),
                checkedAt);
    }

    private UserOpencodeProcessStatusResponse needsInitialization(String message, Instant checkedAt) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.NEEDS_INITIALIZATION,
                true,
                message,
                null,
                null,
                null,
                null,
                null,
                checkedAt,
                UserOpencodeServiceStatus.UNASSIGNED,
                null);
    }

    private UserOpencodeProcessFileRoutingAffinity needsInitializationAffinity(String message, Instant checkedAt) {
        return new UserOpencodeProcessFileRoutingAffinity(
                UserOpencodeProcessAvailability.NEEDS_INITIALIZATION,
                true,
                message,
                null,
                null,
                null,
                null,
                null,
                checkedAt);
    }

    private UserOpencodeProcessStatusResponse needsInitialization(
            String message,
            UserOpencodeProcessBinding binding,
            Instant checkedAt) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.NEEDS_INITIALIZATION,
                true,
                message,
                null,
                null,
                null,
                null,
                null,
                checkedAt,
                UserOpencodeServiceStatus.NOT_RUNNING,
                serviceAddress(binding));
    }

    private UserOpencodeProcessFileRoutingAffinity needsInitializationAffinity(
            String message,
            UserOpencodeProcessBinding binding,
            Instant checkedAt) {
        return new UserOpencodeProcessFileRoutingAffinity(
                UserOpencodeProcessAvailability.NEEDS_INITIALIZATION,
                true,
                message,
                null,
                null,
                null,
                null,
                serviceAddress(binding),
                checkedAt);
    }

    private UserOpencodeProcessStatusResponse needsInitialization(
            String message,
            OpencodeServerProcess process,
            Instant checkedAt) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.NEEDS_INITIALIZATION,
                true,
                message,
                null,
                null,
                null,
                null,
                null,
                checkedAt,
                UserOpencodeServiceStatus.NOT_RUNNING,
                serviceAddress(process));
    }

    private UserOpencodeProcessStatusResponse unavailable(String message, Instant checkedAt) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.UNAVAILABLE,
                false,
                message,
                null,
                null,
                null,
                null,
                null,
                checkedAt,
                UserOpencodeServiceStatus.UNASSIGNED,
                null);
    }

    private UserOpencodeProcessFileRoutingAffinity unavailableAffinity(String message, Instant checkedAt) {
        return new UserOpencodeProcessFileRoutingAffinity(
                UserOpencodeProcessAvailability.UNAVAILABLE,
                false,
                message,
                null,
                null,
                null,
                null,
                null,
                checkedAt);
    }

    private UserOpencodeProcessStatusResponse unavailable(
            String message,
            UserOpencodeProcessBinding binding,
            Instant checkedAt) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.UNAVAILABLE,
                false,
                message,
                null,
                null,
                null,
                null,
                null,
                checkedAt,
                UserOpencodeServiceStatus.NOT_RUNNING,
                serviceAddress(binding));
    }

    private UserOpencodeProcessFileRoutingAffinity unavailableAffinity(
            String message,
            UserOpencodeProcessBinding binding,
            Instant checkedAt) {
        return new UserOpencodeProcessFileRoutingAffinity(
                UserOpencodeProcessAvailability.UNAVAILABLE,
                false,
                message,
                null,
                null,
                null,
                null,
                serviceAddress(binding),
                checkedAt);
    }

    private UserOpencodeProcessStatusResponse unavailable(
            String message,
            OpencodeServerProcess process,
            Instant checkedAt) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.UNAVAILABLE,
                false,
                message,
                null,
                null,
                null,
                null,
                null,
                checkedAt,
                UserOpencodeServiceStatus.NOT_RUNNING,
                serviceAddress(process));
    }

    private String serviceAddress(UserOpencodeProcessBinding binding) {
        if (binding == null || binding.linuxServerId() == null) {
            return null;
        }
        return binding.linuxServerId().value() + ":" + binding.port();
    }

    private String serviceAddress(OpencodeServerProcess process) {
        if (process == null) {
            return null;
        }
        return addressResolver.serviceAddress(process.baseUrl(), process.port());
    }

    private String statusFailureMessage(OpencodeProcessStatusProbe probe) {
        return probe.status() == OpencodeProcessProbeStatus.NOT_STARTED
                ? "opencode 进程未启动，需要重新初始化"
                : "opencode 进程健康检测失败，需要重新初始化";
    }

    private void validateAgent(String agentId) {
        if (agentId == null || !OPENCODE_AGENT_ID.equals(agentId.trim().toLowerCase())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "当前只支持 opencode 用户进程");
        }
    }

    private String normalizeOperationId(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return null;
        }
        String normalized = operationId.trim();
        if (!OPERATION_ID_PATTERN.matcher(normalized).matches()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "operationId 格式无效",
                    Map.of("operationId", normalized));
        }
        return normalized;
    }

    private PlatformException unavailableException(String message) {
        return new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, message, Map.of("agentId", OPENCODE_AGENT_ID));
    }

    private static OpencodeProcessHeartbeatStore disabledHeartbeatStore() {
        return new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { }
            @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
            @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
            @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
            @Override public void cleanupExpiredHeartbeats() { }
        };
    }
}
