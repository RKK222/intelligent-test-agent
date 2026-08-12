package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextWorkspaceMutation;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
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
    private final BackendJavaRouteResolver routeResolver;
    private final ManagedWorkspacePathResolver pathResolver;
    private final Clock clock;
    private final ConversationContextStore conversationContextStore;
    private final ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer;
    private final AppSourceRepository appSourceRepository;
    private LocalClientWorkspaceRepository localWorkspaceRepository;
    private LocalClientConnectionStore localConnectionStore;

    /**
     * 生产构造器使用系统时钟。
     */
    @Autowired
    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            ManagedWorkspacePathResolver pathResolver,
            ConversationContextStore conversationContextStore,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer,
            AppSourceRepository appSourceRepository) {
        this(
                workspaceRepository,
                assignmentService,
                routeResolver,
                pathResolver,
                Clock.systemUTC(),
                conversationContextStore,
                workspaceAccessAuthorizer,
                appSourceRepository);
    }

    /**
     * 测试构造器允许固定时钟。
     */
    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            Clock clock) {
        this(workspaceRepository, assignmentService, routeResolver, ManagedWorkspacePathResolver.legacyOnly(), clock);
    }

    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            ManagedWorkspacePathResolver pathResolver,
            Clock clock) {
        this(workspaceRepository, assignmentService, routeResolver, pathResolver, clock, null);
    }

    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            ManagedWorkspacePathResolver pathResolver,
            Clock clock,
            ConversationContextStore conversationContextStore) {
        this(
                workspaceRepository,
                assignmentService,
                routeResolver,
                pathResolver,
                clock,
                conversationContextStore,
                (userId, workspaceId) -> { },
                null);
    }

    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            ManagedWorkspacePathResolver pathResolver,
            Clock clock,
            ConversationContextStore conversationContextStore,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer) {
        this(
                workspaceRepository,
                assignmentService,
                routeResolver,
                pathResolver,
                clock,
                conversationContextStore,
                workspaceAccessAuthorizer,
                null);
    }

    /** 测试与生产共用的完整构造器，AppSource 映射用于禁止 replica Workspace 本机降级。 */
    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            ManagedWorkspacePathResolver pathResolver,
            Clock clock,
            ConversationContextStore conversationContextStore,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer,
            AppSourceRepository appSourceRepository) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.conversationContextStore = conversationContextStore;
        this.workspaceAccessAuthorizer = Objects.requireNonNull(
                workspaceAccessAuthorizer,
                "workspaceAccessAuthorizer must not be null");
        this.appSourceRepository = appSourceRepository;
    }

    /**
     * 兼容旧单元测试构造器。
     */
    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings,
            Clock clock) {
        this(workspaceRepository, assignmentService, new BackendJavaRouteResolver(heartbeatStore, settings, clock), clock);
    }

    /**
     * 兼容旧单元测试构造器。
     */
    public WorkspaceFileRoutingService(
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings) {
        this(workspaceRepository, assignmentService, heartbeatStore, settings, Clock.systemUTC());
    }

    /**
     * 根据当前用户 opencode 进程定位工作空间文件 WebSocket 所在后端。
     */
    public WorkspaceFileRouteResponse routeWorkspace(UserId userId, String agentId, WorkspaceId workspaceId, String traceId) {
        // 普通文件入口始终按当前登录用户校验归属；跨用户排查只能走独立授权和审计通道。
        workspaceAccessAuthorizer.requireFileAccess(userId, workspaceId, false);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
        LocalClientWorkspaceBinding localBinding = localWorkspaceRepository == null
                ? null
                : localWorkspaceRepository.findByWorkspaceId(workspaceId).orElse(null);
        if (localBinding != null) {
            return routeLocalWorkspace(userId, localBinding);
        }
        UserOpencodeProcessFileRoutingAffinity process = assignmentService.fileRoutingAffinity(userId, agentId, traceId);
        String agentLinuxServerId = readyLinuxServerId(process, workspaceId.value());
        String workspaceLinuxServerId = workspace.linuxServerId() == null ? agentLinuxServerId : workspace.linuxServerId();
        AppSourceReplica appSourceReplica = appSourceReplica(workspaceId);
        if (appSourceReplica != null) {
            return routeAppSourceReplica(
                    workspaceId, workspaceLinuxServerId, agentLinuxServerId, appSourceReplica);
        }
        if (!workspaceLinuxServerId.equals(agentLinuxServerId)) {
            workspace = rebindStaleWorkspaceIfSafe(workspace, workspaceLinuxServerId, agentLinuxServerId, traceId);
            workspaceLinuxServerId = workspace.linuxServerId();
            if (!workspaceLinuxServerId.equals(agentLinuxServerId)) {
                throw workspaceServerConflict(workspaceId, workspaceLinuxServerId, agentLinuxServerId);
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
     * 目录选择器严格路由到持有指定客户端 generation 的 Java；上报 IP、端口不参与选择。
     */
    public WorkspaceFileRouteResponse routeLocalDirectoryPicker(
            UserId userId,
            LocalClientInstanceId clientInstanceId) {
        LocalClientConnectionRoute route = requireLocalRoute(clientInstanceId, userId);
        BackendJavaProcess backend = routeResolver.requireBackend(route.backendProcessId());
        return new WorkspaceFileRouteResponse(
                null,
                null,
                trimTrailingSlash(backend.listenUrl()),
                WEB_SOCKET_PATH,
                routeResolver.isCurrent(route.backendProcessId()),
                null,
                RuntimeKind.LOCAL_CLIENT,
                clientInstanceId.value(),
                route.connectionGeneration(),
                null,
                true);
    }

    /** 可选装配保持既有单元测试构造器不变；生产环境由 Spring 注入本地客户端仓储。 */
    @Autowired(required = false)
    void configureLocalClientRouting(
            LocalClientWorkspaceRepository localWorkspaceRepository,
            LocalClientConnectionStore localConnectionStore) {
        this.localWorkspaceRepository = Objects.requireNonNull(
                localWorkspaceRepository, "localWorkspaceRepository must not be null");
        this.localConnectionStore = Objects.requireNonNull(
                localConnectionStore, "localConnectionStore must not be null");
    }

    private WorkspaceFileRouteResponse routeLocalWorkspace(
            UserId userId,
            LocalClientWorkspaceBinding binding) {
        if (!binding.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无权访问其他用户的本地工作区");
        }
        LocalClientConnectionRoute route = requireLocalRoute(binding.clientInstanceId(), userId);
        BackendJavaProcess backend = routeResolver.requireBackend(route.backendProcessId());
        return new WorkspaceFileRouteResponse(
                binding.workspaceId().value(),
                null,
                trimTrailingSlash(backend.listenUrl()),
                WEB_SOCKET_PATH,
                routeResolver.isCurrent(route.backendProcessId()),
                null,
                RuntimeKind.LOCAL_CLIENT,
                binding.clientInstanceId().value(),
                route.connectionGeneration(),
                binding.rootDigest(),
                true);
    }

    private LocalClientConnectionRoute requireLocalRoute(
            LocalClientInstanceId clientInstanceId,
            UserId userId) {
        if (localConnectionStore == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "本地客户端路由服务未装配");
        }
        LocalClientConnectionRoute route = localConnectionStore.find(clientInstanceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "本地客户端离线",
                        Map.of("clientInstanceId", clientInstanceId.value())));
        if (!route.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端不属于当前用户");
        }
        // requireBackend 会按 backendProcessId 精确解析在线 Java，禁止按服务器或上报地址降级。
        routeResolver.requireBackend(route.backendProcessId());
        return route;
    }

    /**
     * 按工作区权威服务器定位只读排查文件通道，不借用 actor 的 opencode 进程归属，也不做本机降级或重绑。
     */
    public WorkspaceFileRouteResponse routeSupportWorkspace(WorkspaceId workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .filter(item -> item.status() == com.enterprise.testagent.domain.workspace.WorkspaceStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
        String workspaceLinuxServerId = workspace.linuxServerId();
        if (workspaceLinuxServerId == null || workspaceLinuxServerId.isBlank()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "工作空间尚未绑定权威服务器",
                    Map.of("workspaceId", workspaceId.value()));
        }
        AppSourceReplica replica = appSourceReplica(workspaceId);
        if (replica != null) {
            boolean currentReadyGeneration = replica.status() == AppSourceReplicaStatus.READY
                    && appSourceRepository.findSlot(replica.repositoryId())
                            .map(slot -> Objects.equals(slot.activeGeneration(), replica.generation()))
                            .orElse(false);
            if (!currentReadyGeneration || !workspaceLinuxServerId.equals(replica.linuxServerId().value())) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "应用源码工作区副本路由不一致",
                        Map.of("workspaceId", workspaceId.value()));
            }
            workspaceLinuxServerId = replica.linuxServerId().value();
        }
        BackendJavaProcess backend = backendFor(new LinuxServerId(workspaceLinuxServerId));
        return new WorkspaceFileRouteResponse(
                workspaceId.value(),
                workspaceLinuxServerId,
                trimTrailingSlash(backend.listenUrl()),
                WEB_SOCKET_PATH,
                routeResolver.isCurrent(backend.backendProcessId()),
                null);
    }

    private AppSourceReplica appSourceReplica(WorkspaceId workspaceId) {
        return appSourceRepository == null
                ? null
                : appSourceRepository.findReplicaByRuntimeWorkspaceId(workspaceId.value()).orElse(null);
    }

    private WorkspaceFileRouteResponse routeAppSourceReplica(
            WorkspaceId workspaceId,
            String workspaceLinuxServerId,
            String agentLinuxServerId,
            AppSourceReplica replica) {
        String replicaLinuxServerId = replica.linuxServerId().value();
        boolean currentReadyGeneration = replica.status() == AppSourceReplicaStatus.READY
                && appSourceRepository.findSlot(replica.repositoryId())
                        .map(slot -> Objects.equals(slot.activeGeneration(), replica.generation()))
                        .orElse(false);
        if (!currentReadyGeneration
                || !replicaLinuxServerId.equals(workspaceLinuxServerId)
                || !replicaLinuxServerId.equals(agentLinuxServerId)) {
            // AppSource 文件身份由 current generation 的 READY replica 决定，禁止按错误 Workspace 行或本机路径降级。
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "应用源码工作区副本路由不一致",
                    Map.of(
                            "workspaceId", workspaceId.value(),
                            "workspaceLinuxServerId", workspaceLinuxServerId,
                            "agentLinuxServerId", agentLinuxServerId,
                            "replicaLinuxServerId", replicaLinuxServerId,
                            "replicaGeneration", replica.generation()));
        }
        BackendJavaProcess backend = backendFor(replica.linuxServerId());
        return new WorkspaceFileRouteResponse(
                workspaceId.value(),
                replicaLinuxServerId,
                trimTrailingSlash(backend.listenUrl()),
                WEB_SOCKET_PATH,
                true,
                null);
    }

    private PlatformException workspaceServerConflict(
            WorkspaceId workspaceId,
            String workspaceLinuxServerId,
            String agentLinuxServerId) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                "工作空间与 agent 不在同一服务器",
                Map.of(
                        "workspaceId", workspaceId.value(),
                        "workspaceLinuxServerId", workspaceLinuxServerId,
                        "agentLinuxServerId", agentLinuxServerId));
    }

    /**
     * 本地服务器身份变化或数据库切换后，历史 workspace 可能仍绑定旧服务器身份。
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
        if (!routeResolver.isCurrent(agentLinuxServerId)) {
            return workspace;
        }
        if (hasReadyBackend(new LinuxServerId(staleLinuxServerId))) {
            return workspace;
        }
        if (!rootPathAvailable(workspace.rootPath())) {
            return workspace;
        }
        Workspace rebound = workspace.withLinuxServerId(agentLinuxServerId, traceId, Instant.now(clock));
        if (conversationContextStore == null) {
            return workspaceRepository.save(rebound);
        }
        ConversationContextWorkspaceMutation mutation =
                conversationContextStore.beginWorkspaceMutation(rebound.workspaceId());
        Workspace saved;
        try {
            saved = workspaceRepository.save(rebound);
        } catch (RuntimeException exception) {
            try {
                conversationContextStore.abortWorkspaceMutation(mutation);
            } catch (RuntimeException abortFailure) {
                exception.addSuppressed(abortFailure);
            }
            throw exception;
        }
        conversationContextStore.completeWorkspaceMutation(mutation);
        return saved;
    }

    private boolean hasReadyBackend(LinuxServerId linuxServerId) {
        if (routeResolver.isCurrent(linuxServerId)) {
            return true;
        }
        try {
            routeResolver.requireBackend(linuxServerId);
            return true;
        } catch (PlatformException exception) {
            return false;
        }
    }

    private boolean rootPathAvailable(String rootPath) {
        try {
            return Files.isDirectory(pathResolver.resolve(rootPath).toRealPath());
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
        List<BackendRuntimeSnapshot> snapshots = routeResolver.liveBackendSnapshots(BACKEND_LIMIT);
        Map<String, LinuxServer> servers = new LinkedHashMap<>();
        Map<String, BackendJavaProcess> backendByServer = new LinkedHashMap<>();
        for (BackendRuntimeSnapshot snapshot : snapshots) {
            servers.putIfAbsent(snapshot.linuxServer().linuxServerId().value(), snapshot.linuxServer());
            BackendJavaProcess backend = snapshot.backendProcess();
            backendByServer.putIfAbsent(backend.linuxServerId().value(), backend);
        }
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
        return routeResolver.requireBackend(linuxServerId);
    }

    private String defaultDirectory(LinuxServer server, BackendJavaProcess backend) {
        if (server != null) {
            Object directory = server.capacitySummary().get("backendWorkingDirectory");
            if (directory instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        if (routeResolver.isCurrent(backend.linuxServerId())) {
            return Path.of("").toAbsolutePath().normalize().toString();
        }
        return "";
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
