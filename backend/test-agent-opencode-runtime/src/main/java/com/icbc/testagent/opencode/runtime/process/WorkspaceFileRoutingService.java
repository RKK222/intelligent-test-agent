package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.file.Files;
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
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final ManagerControlSettings settings;
    private final Clock clock;

    /**
     * 生产构造器使用系统时钟。
     */
    @Autowired
    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings) {
        this(workspaceRepository, assignmentService, heartbeatStore, settings, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟。
     */
    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings,
            Clock clock) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 根据当前用户 opencode 进程定位工作空间文件 WebSocket 所在后端。
     */
    public WorkspaceFileRouteResponse routeWorkspace(UserId userId, String agentId, WorkspaceId workspaceId, String traceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
        UserOpencodeProcessFileRoutingAffinity process = assignmentService.fileRoutingAffinity(userId, agentId, traceId);
        String agentLinuxServerId = readyLinuxServerId(process, workspaceId.value());
        String workspaceLinuxServerId = workspace.linuxServerId() == null ? agentLinuxServerId : workspace.linuxServerId();
        if (!workspaceLinuxServerId.equals(agentLinuxServerId)) {
            workspace = rebindStaleWorkspaceIfSafe(workspace, workspaceLinuxServerId, agentLinuxServerId, traceId);
            workspaceLinuxServerId = workspace.linuxServerId();
            if (!workspaceLinuxServerId.equals(agentLinuxServerId)) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "工作空间与 agent 不在同一服务器",
                        Map.of(
                                "workspaceId", workspaceId.value(),
                                "workspaceLinuxServerId", workspaceLinuxServerId,
                                "agentLinuxServerId", agentLinuxServerId));
            }
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
     * 本地 IP 变化或数据库切换后，历史 workspace 可能仍绑定旧服务器 IP。
     *
     * <p>只有在当前 agent 已经落在本后端、旧服务器没有存活后端快照、且 workspace 根目录在本机可访问时，
     * 才把 workspace 回绑到当前 agent 服务器。多机部署中旧服务器仍在线或本机没有该目录时继续返回冲突，
     * 避免把真实远端工作区错误迁移到当前机器。
     */
    private Workspace rebindStaleWorkspaceIfSafe(
            Workspace workspace,
            String staleLinuxServerId,
            String agentLinuxServerId,
            String traceId) {
        if (!settings.linuxServerId().value().equals(agentLinuxServerId)) {
            return workspace;
        }
        if (hasReadyBackend(new LinuxServerId(staleLinuxServerId))) {
            return workspace;
        }
        if (!rootPathAvailable(workspace.rootPath())) {
            return workspace;
        }
        Workspace rebound = workspace.withLinuxServerId(agentLinuxServerId, traceId, Instant.now(clock));
        return workspaceRepository.save(rebound);
    }

    private boolean hasReadyBackend(LinuxServerId linuxServerId) {
        if (settings.linuxServerId().equals(linuxServerId)) {
            return true;
        }
        return readyBackends().stream().anyMatch(backend -> backend.linuxServerId().equals(linuxServerId));
    }

    private boolean rootPathAvailable(String rootPath) {
        try {
            return Files.isDirectory(Path.of(rootPath).toRealPath());
        } catch (Exception ignored) {
            return false;
        }
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
        List<BackendRuntimeSnapshot> snapshots = liveBackendSnapshots();
        Map<String, LinuxServer> servers = new LinkedHashMap<>();
        Map<String, BackendJavaProcess> backendByServer = new LinkedHashMap<>();
        for (BackendRuntimeSnapshot snapshot : snapshots) {
            servers.putIfAbsent(snapshot.linuxServer().linuxServerId().value(), snapshot.linuxServer());
            BackendJavaProcess backend = snapshot.backendProcess();
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

    private String readyLinuxServerId(UserOpencodeProcessFileRoutingAffinity process, String workspaceId) {
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
        return liveBackendSnapshots().stream()
                .map(BackendRuntimeSnapshot::backendProcess)
                .toList();
    }

    private List<BackendRuntimeSnapshot> liveBackendSnapshots() {
        return heartbeatStore.liveBackendSnapshots().stream()
                .limit(BACKEND_LIMIT)
                .toList();
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
