package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 用户 opencode 进程分配服务，负责状态检查、初始化和 Run 目标节点投影。
 */
@Service
public class UserOpencodeProcessAssignmentService {

    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final int CONTAINER_CANDIDATE_LIMIT = 100;
    private static final String CONFIG_PATH = "/data/opencode/.config/opencode/";

    private final OpencodeProcessManagementRepository repository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final OpencodeProcessManagerGateway gateway;
    private final BackendJavaProcessLifecycleService backendLifecycle;

    /**
     * 注入进程管理 Repository、兼容节点 Repository 和管理进程 gateway。
     */
    public UserOpencodeProcessAssignmentService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            BackendJavaProcessLifecycleService backendLifecycle) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.backendLifecycle = Objects.requireNonNull(backendLifecycle, "backendLifecycle must not be null");
    }

    /**
     * 查询当前用户 opencode 进程状态；不自动启动进程。
     */
    public UserOpencodeProcessStatusResponse status(UserId userId, String agentId, String traceId) {
        validateAgent(agentId);
        Instant now = Instant.now();
        Optional<UserOpencodeProcessBinding> binding = repository.findUserBinding(userId, OPENCODE_AGENT_ID);
        if (binding.isEmpty()) {
            return hasInitializableContainer()
                    ? needsInitialization("需要初始化 opencode 进程", now)
                    : unavailable("没有可用的 opencode 容器", now);
        }
        Optional<OpencodeServerProcess> process = activeProcess(binding.get());
        if (process.isEmpty()) {
            return canRebuildOn(binding.get().linuxServerId())
                    ? needsInitialization("opencode 进程不可用，需要重新初始化", now)
                    : unavailable("原 Linux 服务器没有可用的 opencode 容器", now);
        }
        OpencodeServerProcess current = process.get();
        OpencodeProcessHealthResult health = checkHealth(current, traceId);
        if (health.healthy()) {
            OpencodeServerProcess refreshed = refreshProcess(current, OpencodeServerProcessStatus.RUNNING, health.message(), now, traceId);
            repository.saveOpencodeServerProcess(refreshed);
            return ready(refreshed, "opencode 进程可用", now);
        }
        repository.saveOpencodeServerProcess(refreshProcess(current, OpencodeServerProcessStatus.UNHEALTHY, health.message(), now, traceId));
        return canRebuildOn(binding.get().linuxServerId())
                ? needsInitialization("opencode 进程健康检测失败，需要重新初始化", now)
                : unavailable("opencode 进程健康检测失败，且原 Linux 服务器没有可用容器", now);
    }

    /**
     * 初始化或重建当前用户 opencode 进程；真实启动由 gateway 完成。
     */
    public UserOpencodeProcessStatusResponse initialize(UserId userId, String agentId, String traceId) {
        validateAgent(agentId);
        Instant now = Instant.now();
        Optional<UserOpencodeProcessBinding> existingBinding = repository.findUserBinding(userId, OPENCODE_AGENT_ID);
        Optional<OpencodeServerProcess> existingProcess = existingBinding.flatMap(binding -> activeProcess(binding).or(() ->
                repository.findOpencodeServerProcessById(binding.processId())));
        if (existingProcess.isPresent() && isRecoverableProcess(existingProcess.get())) {
            OpencodeProcessHealthResult health = checkHealth(existingProcess.get(), traceId);
            if (health.healthy()) {
                OpencodeServerProcess refreshed = refreshProcess(existingProcess.get(), OpencodeServerProcessStatus.RUNNING, health.message(), now, traceId);
                repository.saveOpencodeServerProcess(refreshed);
                ExecutionNode node = projectExecutionNode(refreshed, now, traceId);
                executionNodeRepository.save(node);
                return ready(refreshed, "opencode 进程可用", now);
            }
        }

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
        ExecutionNode node = projectExecutionNode(refreshed, now, traceId);
        executionNodeRepository.save(node);
        return new UserOpencodeProcessAssignment(node);
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

    private OpencodeProcessStartCommand startCommand(UserId userId, OpencodeContainer container, String traceId) {
        int port = firstAvailablePort(container)
                .orElseThrow(() -> unavailableException("没有可用的 opencode 端口"));
        String baseUrl = "http://" + container.linuxServerId().value() + ":" + port;
        return new OpencodeProcessStartCommand(
                userId,
                container.linuxServerId(),
                container.containerId(),
                port,
                baseUrl,
                "/data/opencode/session/" + port,
                CONFIG_PATH,
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
                checkedAt);
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
                checkedAt);
    }

    private void validateAgent(String agentId) {
        if (agentId == null || !OPENCODE_AGENT_ID.equals(agentId.trim().toLowerCase())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "当前只支持 opencode 用户进程");
        }
    }

    private PlatformException unavailableException(String message) {
        return new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, message, Map.of("agentId", OPENCODE_AGENT_ID));
    }
}
