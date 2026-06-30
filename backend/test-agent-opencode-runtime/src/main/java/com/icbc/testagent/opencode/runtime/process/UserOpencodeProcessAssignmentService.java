package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
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
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户 opencode 进程分配服务，负责状态检查、初始化和 Run 目标节点投影。
 */
@Service
public class UserOpencodeProcessAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(UserOpencodeProcessAssignmentService.class);
    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final int CONTAINER_CANDIDATE_LIMIT = 100;
    private static final String PARAM_OPENCODE_SESSION_DIR = "OPENCODE_SESSION_DIR";
    private static final String PARAM_OPENCODE_PUBLIC_CONFIG_DIR = "OPENCODE_PUBLIC_CONFIG_DIR";
    private static final String LOCAL_DIRECT_PROCESS_ID = "ocp_local_direct";
    private static final String LOCAL_DIRECT_CONTAINER_ID = "ctr_local_direct";
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
    private final LocalDirectSettings localDirectSettings;

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
        this(repository, EMPTY_PARAMETER_VALUES, executionNodeRepository, gateway, backendLifecycle, heartbeatStore, LocalDirectSettings.disabled());
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
        this(repository, commonParameterValues, executionNodeRepository, gateway, backendLifecycle, disabledHeartbeatStore(), LocalDirectSettings.disabled());
    }

    /**
     * 注入完整依赖，包含本地开发短路模式设置；Spring 容器默认走这个构造器。
     */
    @Autowired
    public UserOpencodeProcessAssignmentService(
            OpencodeProcessManagementRepository repository,
            CommonParameterValues commonParameterValues,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            BackendJavaProcessLifecycleService backendLifecycle,
            OpencodeProcessHeartbeatStore heartbeatStore,
            LocalDirectSettings localDirectSettings) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.backendLifecycle = Objects.requireNonNull(backendLifecycle, "backendLifecycle must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.localDirectSettings = Objects.requireNonNull(localDirectSettings, "localDirectSettings must not be null");
    }

    /**
     * 查询当前用户 opencode 进程状态；不自动启动进程。
     */
    public UserOpencodeProcessStatusResponse status(UserId userId, String agentId, String traceId) {
        validateAgent(agentId);
        Instant now = Instant.now();
        if (localDirectSettings.enabled()) {
            return localDirectStatus(userId, now, traceId);
        }
        Optional<UserOpencodeProcessBinding> binding = repository.findUserBinding(userId, OPENCODE_AGENT_ID)
                .filter(item -> item.status() == UserOpencodeProcessBindingStatus.ACTIVE);
        if (binding.isEmpty()) {
            return hasInitializableContainer()
                    ? needsInitialization("需要初始化 opencode 进程", now)
                    : unavailable("没有可用的 opencode 容器", now);
        }
        Optional<OpencodeServerProcess> process = activeProcess(binding.get());
        if (process.isEmpty()) {
            return canRebuildOn(binding.get().linuxServerId())
                    ? needsInitialization("opencode 进程不可用，需要重新初始化", binding.get(), now)
                    : unavailable("原 Linux 服务器没有可用的 opencode 容器", binding.get(), now);
        }
        OpencodeServerProcess current = process.get();
        OpencodeProcessHealthResult health;
        try {
            health = checkHealth(current, traceId);
        } catch (PlatformException exception) {
            return unavailable("opencode 进程健康状态暂无法确认：" + exception.getMessage(), current, now);
        }
        if (health.healthy()) {
            OpencodeServerProcess refreshed = refreshProcess(current, OpencodeServerProcessStatus.RUNNING, health.message(), now, traceId);
            repository.saveOpencodeServerProcess(refreshed);
            heartbeatStore.recordOpencodeHeartbeat(refreshed.processId(), now);
            return ready(refreshed, "opencode 进程可用", now);
        }
        repository.saveOpencodeServerProcess(refreshProcess(current, OpencodeServerProcessStatus.UNHEALTHY, health.message(), now, traceId));
        return canRebuildOn(binding.get().linuxServerId())
                ? needsInitialization("opencode 进程健康检测失败，需要重新初始化", current, now)
                : unavailable("opencode 进程健康检测失败，且当前没有可用容器", current, now);
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
        if (localDirectSettings.enabled()) {
            OpencodeServerProcess process = synthesizeLocalDirectProcess(userId, now, traceId);
            return readyAffinity(process, "本地开发模式：直连 " + localDirectSettings.baseUrl(), now);
        }
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
        if (localDirectSettings.enabled()) {
            return Optional.empty();
        }
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
        validateAgent(agentId);
        Instant now = Instant.now();
        if (localDirectSettings.enabled()) {
            log.info("本地开发短路模式：跳过 gateway 拉起与 topology 校验，直接返回合成 READY userId={} baseUrl={}",
                    userId == null ? null : userId.value(), localDirectSettings.baseUrl());
            return localDirectStatus(userId, now, traceId);
        }
        Optional<UserOpencodeProcessBinding> existingBinding = repository.findUserBinding(userId, OPENCODE_AGENT_ID);
        Optional<OpencodeServerProcess> existingProcess = existingBinding.flatMap(binding -> activeProcess(binding).or(() ->
                repository.findOpencodeServerProcessById(binding.processId())));
        if (existingProcess.isPresent() && isRecoverableProcess(existingProcess.get())) {
            OpencodeProcessHealthResult health = checkHealth(existingProcess.get(), traceId);
            if (health.healthy()) {
                OpencodeServerProcess refreshed = refreshProcess(existingProcess.get(), OpencodeServerProcessStatus.RUNNING, health.message(), now, traceId);
                repository.saveOpencodeServerProcess(refreshed);
                heartbeatStore.recordOpencodeHeartbeat(refreshed.processId(), now);
                ExecutionNode node = projectExecutionNode(refreshed, now, traceId);
                executionNodeRepository.save(node);
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
        OpencodeProcessStartCommand command = startCommand(userId, chooseContainer(candidates), traceId);
        OpencodeProcessStartResult started = gateway.startProcess(command);
        if (started == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 管理进程启动未返回结果");
        }
        OpencodeProcessId processId = existingBinding
                .map(UserOpencodeProcessBinding::processId)
                .orElseGet(() -> new OpencodeProcessId(RuntimeIdGenerator.opencodeProcessId()));
        Instant createdAt = existingProcess.map(OpencodeServerProcess::createdAt).orElse(now);
        OpencodeServerProcess process = new OpencodeServerProcess(
                processId,
                userId,
                command.linuxServerId(),
                command.containerId(),
                command.port(),
                started.pid(),
                command.baseUrl(),
                OpencodeServerProcessStatus.RUNNING,
                command.sessionPath(),
                command.configPath(),
                now,
                now,
                started.message() == null ? "started" : started.message(),
                createdAt,
                now,
                traceId);
        repository.saveOpencodeServerProcess(process);
        heartbeatStore.recordOpencodeHeartbeat(process.processId(), now);
        repository.saveUserBinding(new UserOpencodeProcessBinding(
                userId,
                OPENCODE_AGENT_ID,
                process.processId(),
                process.linuxServerId(),
                process.port(),
                UserOpencodeProcessBindingStatus.ACTIVE,
                existingBinding.map(UserOpencodeProcessBinding::createdAt).orElse(now),
                now,
                traceId));
        executionNodeRepository.save(projectExecutionNode(process, now, traceId));
        return ready(process, "opencode 进程可用", now);
    }

    /**
     * Run 启动前解析用户专属运行目标；缺失或不健康时要求前端先初始化。
     */
    public UserOpencodeProcessAssignment requireReadyProcess(UserId userId, String agentId, String traceId) {
        validateAgent(agentId);
        Instant now = Instant.now();
        if (localDirectSettings.enabled()) {
            OpencodeServerProcess process = synthesizeLocalDirectProcess(userId, now, traceId);
            return new UserOpencodeProcessAssignment(projectExecutionNode(process, now, traceId), process.linuxServerId().value());
        }
        UserOpencodeProcessBinding binding = repository.findUserBinding(userId, OPENCODE_AGENT_ID)
                .filter(item -> item.status() == UserOpencodeProcessBindingStatus.ACTIVE)
                .orElseThrow(() -> unavailableException("请先初始化 opencode 进程"));
        OpencodeServerProcess process = repository.findOpencodeServerProcessById(binding.processId())
                .filter(this::isRecoverableProcess)
                .orElseThrow(() -> unavailableException("请先初始化 opencode 进程"));
        OpencodeProcessHealthResult health = checkHealth(process, traceId);
        if (!health.healthy()) {
            repository.saveOpencodeServerProcess(refreshProcess(process, OpencodeServerProcessStatus.UNHEALTHY, health.message(), now, traceId));
            throw unavailableException("opencode 进程不可用，请先初始化");
        }
        OpencodeServerProcess refreshed = refreshProcess(process, OpencodeServerProcessStatus.RUNNING, health.message(), now, traceId);
        repository.saveOpencodeServerProcess(refreshed);
        heartbeatStore.recordOpencodeHeartbeat(refreshed.processId(), now);
        ExecutionNode node = projectExecutionNode(refreshed, now, traceId);
        executionNodeRepository.save(node);
        return new UserOpencodeProcessAssignment(node, refreshed.linuxServerId().value());
    }

    private Optional<OpencodeServerProcess> activeProcess(UserOpencodeProcessBinding binding) {
        if (binding.status() != UserOpencodeProcessBindingStatus.ACTIVE) {
            return Optional.empty();
        }
        return repository.findOpencodeServerProcessById(binding.processId())
                .filter(this::isRecoverableProcess);
    }

    private boolean isRecoverableProcess(OpencodeServerProcess process) {
        return process.status() == OpencodeServerProcessStatus.RUNNING
                || process.status() == OpencodeServerProcessStatus.STARTING
                || process.status() == OpencodeServerProcessStatus.UNHEALTHY;
    }

    private OpencodeProcessHealthResult checkHealth(OpencodeServerProcess process, String traceId) {
        try {
            return gateway.checkHealth(new OpencodeProcessHealthCommand(process.processId(), process.baseUrl(), traceId));
        } catch (PlatformException exception) {
            if (exception.errorCode() == ErrorCode.OPENCODE_UNAVAILABLE) {
                return OpencodeProcessHealthResult.unhealthy(exception.getMessage());
            }
            throw exception;
        }
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
        String baseUrl = "http://" + container.linuxServerId().value() + ":" + port;
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

    private OpencodeServerProcess refreshProcess(
            OpencodeServerProcess process,
            OpencodeServerProcessStatus status,
            String healthMessage,
            Instant now,
            String traceId) {
        return new OpencodeServerProcess(
                process.processId(),
                process.userId(),
                process.linuxServerId(),
                process.containerId(),
                process.port(),
                process.pid(),
                process.baseUrl(),
                status,
                process.sessionPath(),
                process.configPath(),
                process.startedAt(),
                now,
                healthMessage,
                process.createdAt(),
                now,
                traceId);
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

    /**
     * 本地开发短路：合成指向 baseUrl 的 READY 状态响应。
     *
     * <p>不写库，不调用 gateway，不校验 topology / binding / health。生产配置
     * {@link LocalDirectSettings#enabled()}=false 时该方法不会被触发。
     */
    private UserOpencodeProcessStatusResponse localDirectStatus(UserId userId, Instant now, String traceId) {
        OpencodeServerProcess process = synthesizeLocalDirectProcess(userId, now, traceId);
        return ready(process, "本地开发模式：直连 " + localDirectSettings.baseUrl(), now);
    }

    /**
     * 本地开发短路：构造一个满足 {@link OpencodeServerProcess} 校验的合成进程对象。
     */
    private OpencodeServerProcess synthesizeLocalDirectProcess(UserId userId, Instant now, String traceId) {
        String baseUrl = localDirectSettings.baseUrl();
        ParsedBaseUrl parsed = parseBaseUrl(baseUrl);
        return new OpencodeServerProcess(
                new OpencodeProcessId(LOCAL_DIRECT_PROCESS_ID),
                userId == null ? new UserId("usr_local_direct") : userId,
                new LinuxServerId(parsed.host()),
                new OpencodeContainerId(LOCAL_DIRECT_CONTAINER_ID),
                parsed.port(),
                0L,
                "http://" + parsed.host() + ":" + parsed.port(),
                OpencodeServerProcessStatus.RUNNING,
                sessionPath(parsed.port()),
                configPath(),
                now,
                now,
                "local-direct",
                now,
                now,
                traceId);
    }

    /**
     * 解析 baseUrl，提取 host 与 port；如果解析失败则回退到默认 127.0.0.1:4096。
     */
    private ParsedBaseUrl parseBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return new ParsedBaseUrl("127.0.0.1", 4096);
        }
        try {
            URI uri = new URI(baseUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || host.isBlank() || port < 1 || port > 65535) {
                return new ParsedBaseUrl("127.0.0.1", 4096);
            }
            return new ParsedBaseUrl(host, port);
        } catch (URISyntaxException exception) {
            log.warn("local-direct baseUrl 解析失败，回退默认 127.0.0.1:4096 value={} message={}",
                    baseUrl, exception.getMessage());
            return new ParsedBaseUrl("127.0.0.1", 4096);
        }
    }

    /**
     * 解析后的 host/port 简单包装。
     */
    private record ParsedBaseUrl(String host, int port) {
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
        if (process == null || process.linuxServerId() == null) {
            return null;
        }
        return process.linuxServerId().value() + ":" + process.port();
    }

    private void validateAgent(String agentId) {
        if (agentId == null || !OPENCODE_AGENT_ID.equals(agentId.trim().toLowerCase())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "当前只支持 opencode 用户进程");
        }
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
