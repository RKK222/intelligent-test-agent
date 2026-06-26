package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 工作空间文件 WebSocket 路由服务，统一校验 workspace、用户 opencode 进程与目标后端服务器关系。
 */
@Service
public class WorkspaceFileRoutingService {

    public static final String WEB_SOCKET_PATH = "/api/internal/platform/workspace-management/file/ws";
    private static final int BACKEND_LIMIT = 500;

    private final WorkspaceRepository workspaceRepository;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final OpencodeProcessManagementRepository processRepository;
    private final ManagerControlSettings settings;
    private final Clock clock;

    /**
     * 生产构造器使用系统时钟。
     */
    @Autowired
    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeProcessManagementRepository processRepository,
            ManagerControlSettings settings) {
        this(workspaceRepository, assignmentService, processRepository, settings, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟。
     */
    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeProcessManagementRepository processRepository,
            ManagerControlSettings settings,
            Clock clock) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.processRepository = Objects.requireNonNull(processRepository, "processRepository must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 根据当前用户 opencode 进程定位工作空间文件 WebSocket 所在后端。
     */
    public WorkspaceFileRouteResponse routeWorkspace(UserId userId, String agentId, WorkspaceId workspaceId, String traceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
        UserOpencodeProcessStatusResponse process = assignmentService.status(userId, agentId, traceId);
        String agentLinuxServerId = readyLinuxServerId(process, workspaceId.value());
        String workspaceLinuxServerId = workspace.linuxServerId() == null ? agentLinuxServerId : workspace.linuxServerId();
        if (!workspaceLinuxServerId.equals(agentLinuxServerId)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "工作空间与 agent 不在同一服务器",
                    Map.of(
                            "workspaceId", workspaceId.value(),
                            "workspaceLinuxServerId", workspaceLinuxServerId,
                            "agentLinuxServerId", agentLinuxServerId));
        }
        BackendJavaProcess backend = backendFor(new LinuxServerId(workspaceLinuxServerId));
        return new WorkspaceFileRouteResponse(
                workspaceId.value(),
                workspaceLinuxServerId,
                trimTrailingSlash(backend.listenUrl()),
                WEB_SOCKET_PATH,
                true,
                null);
    }

    /**
     * 超级管理员查询可直连文件 WebSocket 的后端服务器列表。
     */
    public List<WorkspaceBackendServerResponse> listBackendServers(UserId userId, String agentId, String traceId) {
        String agentLinuxServerId = null;
        try {
            UserOpencodeProcessStatusResponse process = assignmentService.status(userId, agentId, traceId);
            if (process.status() == UserOpencodeProcessAvailability.READY) {
                agentLinuxServerId = process.linuxServerId();
            }
        } catch (PlatformException ignored) {
            agentLinuxServerId = null;
        }
        Map<String, LinuxServer> servers = new LinkedHashMap<>();
        for (LinuxServer server : processRepository.findLinuxServers(BACKEND_LIMIT)) {
            servers.put(server.linuxServerId().value(), server);
        }
        Map<String, BackendJavaProcess> backendByServer = new LinkedHashMap<>();
        for (BackendJavaProcess backend : readyBackends()) {
            backendByServer.putIfAbsent(backend.linuxServerId().value(), backend);
        }
        backendByServer.putIfAbsent(settings.linuxServerId().value(), currentBackend());
        String currentAgentServer = agentLinuxServerId;
        return backendByServer.values().stream()
                .sorted(Comparator.comparing(backend -> backend.linuxServerId().value()))
                .map(backend -> {
                    LinuxServer server = servers.get(backend.linuxServerId().value());
                    return new WorkspaceBackendServerResponse(
                            backend.linuxServerId().value(),
                            server == null ? backend.linuxServerId().value() : server.name(),
                            trimTrailingSlash(backend.listenUrl()),
                            WEB_SOCKET_PATH,
                            defaultDirectory(server, backend),
                            backend.linuxServerId().value().equals(currentAgentServer));
                })
                .toList();
    }

    private String readyLinuxServerId(UserOpencodeProcessStatusResponse process, String workspaceId) {
        if (process.status() != UserOpencodeProcessAvailability.READY || process.linuxServerId() == null || process.linuxServerId().isBlank()) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "当前用户 opencode 进程不可用",
                    Map.of("workspaceId", workspaceId, "status", process.status().name()));
        }
        return process.linuxServerId();
    }

    private BackendJavaProcess backendFor(LinuxServerId linuxServerId) {
        return readyBackends().stream()
                .filter(backend -> backend.linuxServerId().equals(linuxServerId))
                .findFirst()
                .orElseGet(() -> {
                    if (settings.linuxServerId().equals(linuxServerId)) {
                        return currentBackend();
                    }
                    throw new PlatformException(
                            ErrorCode.OPENCODE_UNAVAILABLE,
                            "目标服务器后端不可用",
                            Map.of("linuxServerId", linuxServerId.value()));
                });
    }

    private List<BackendJavaProcess> readyBackends() {
        Instant minHeartbeatAt = Instant.now(clock).minus(settings.backendStaleAfter());
        return processRepository.findReadyBackendJavaProcesses(minHeartbeatAt, BACKEND_LIMIT);
    }

    private BackendJavaProcess currentBackend() {
        Instant now = Instant.now(clock);
        return new BackendJavaProcess(
                new com.icbc.testagent.domain.opencodeprocess.BackendProcessId("bjp_current_backend"),
                settings.linuxServerId(),
                settings.listenUrl(),
                com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus.READY,
                now,
                now,
                now,
                now,
                "trace_current_backend");
    }

    private String defaultDirectory(LinuxServer server, BackendJavaProcess backend) {
        if (server != null) {
            Object directory = server.capacitySummary().get("backendWorkingDirectory");
            if (directory instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        if (backend.linuxServerId().equals(settings.linuxServerId())) {
            return Path.of("").toAbsolutePath().normalize().toString();
        }
        return "";
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
