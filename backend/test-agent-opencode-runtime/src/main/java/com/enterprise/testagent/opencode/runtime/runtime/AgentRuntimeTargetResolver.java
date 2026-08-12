package com.enterprise.testagent.opencode.runtime.runtime;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.agent.runtime.AgentCreateSessionCommand;
import com.enterprise.testagent.agent.runtime.AgentCreateSessionResult;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentSessionExistsCommand;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionHistoryRepository;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionRuntimeTargetRepository;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * agent runtime 目标解析服务，统一维护用户进程节点、固定节点 fallback 和远端 session 绑定规则。
 */
@Service
public class AgentRuntimeTargetResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRuntimeTargetResolver.class);

    private final WorkspaceRepository workspaceRepository;
    private final SessionRepository sessionRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final AgentRuntimeRegistry agentRuntimeRegistry;
    private final AgentSessionBindingRepository agentSessionBindingRepository;
    private final UserOpencodeProcessAssignmentService userProcessAssignmentService;
    private final ManagedWorkspacePathResolver pathResolver;
    private final ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer;
    private SessionHistoryRepository sessionHistoryRepository;
    private LocalClientWorkspaceRepository localClientWorkspaceRepository;
    private LocalClientConnectionStore localClientConnectionStore;
    private SessionRuntimeTargetRepository sessionRuntimeTargetRepository;
    private BackendJavaRouteResolver backendJavaRouteResolver;

    /**
     * 注入 runtime 目标解析所需端口；用户进程服务仅在认证用户访问默认 opencode 时使用。
     */
    @Autowired
    public AgentRuntimeTargetResolver(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver pathResolver,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.agentSessionBindingRepository = Objects.requireNonNull(agentSessionBindingRepository, "agentSessionBindingRepository must not be null");
        this.userProcessAssignmentService = userProcessAssignmentService;
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.workspaceAccessAuthorizer = Objects.requireNonNull(
                workspaceAccessAuthorizer,
                "workspaceAccessAuthorizer must not be null");
    }

    /**
     * 兼容现有内部装配；生产注入必须使用上方构造器接入实时应用成员校验。
     */
    public AgentRuntimeTargetResolver(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver pathResolver) {
        this(
                workspaceRepository,
                sessionRepository,
                executionNodeRepository,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                userProcessAssignmentService,
                pathResolver,
                (userId, workspaceId) -> { });
    }

    public AgentRuntimeTargetResolver(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            UserOpencodeProcessAssignmentService userProcessAssignmentService) {
        this(
                workspaceRepository,
                sessionRepository,
                executionNodeRepository,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                userProcessAssignmentService,
                ManagedWorkspacePathResolver.legacyOnly());
    }

    /**
     * 解析 workspace 级 runtime 调用目标；认证 opencode 用户必须命中自己的 READY 进程。
     */
    public WorkspaceRuntimeTarget workspaceTarget(String agentId, UserId userId, String workspaceId, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        WorkspaceId resolvedWorkspaceId = workspaceId == null || workspaceId.isBlank()
                ? null
                : new WorkspaceId(workspaceId);
        if (resolvedWorkspaceId != null) {
            requireRuntimeWorkspaceAccess(userId, resolvedWorkspaceId);
        }
        if (resolvedWorkspaceId != null) {
            Workspace workspace = findWorkspace(resolvedWorkspaceId);
            LocalClientWorkspaceBinding binding = localBinding(resolvedWorkspaceId);
            if (binding != null) {
                return new WorkspaceRuntimeTarget(
                        runtime,
                        localExecutionNode(binding, userId, traceId),
                        workspace.rootPath());
            }
        }
        ExecutionNode node = resolveUserProcessAssignment(userId, resolvedAgentId, traceId)
                .map(UserOpencodeProcessAssignment::node)
                .orElseGet(this::routableNode);
        if (resolvedWorkspaceId == null) {
            return new WorkspaceRuntimeTarget(runtime, node, null, null);
        }
        Workspace workspace = findWorkspace(resolvedWorkspaceId);
        return new WorkspaceRuntimeTarget(runtime, node, workspaceRoot(workspace), workspace.workspaceId());
    }

    /**
     * 解析 session 级 runtime 调用目标；认证 opencode 用户在节点不一致时自动重建远端 session。
     */
    public SessionRuntimeTarget sessionTarget(String agentId, UserId userId, String sessionId, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        Session session = findSession(new SessionId(sessionId));
        requireAuthenticatedSessionAccess(userId, session);
        requireRuntimeWorkspaceAccess(userId, session.workspaceId());
        Workspace workspace = findWorkspace(session.workspaceId());
        com.enterprise.testagent.domain.session.SessionRuntimeTarget frozenTarget = sessionRuntimeTarget(session);
        if (frozenTarget.runtimeKind() == RuntimeKind.LOCAL_CLIENT) {
            LocalClientWorkspaceBinding binding = requireFrozenLocalBinding(session, frozenTarget);
            ExecutionNode node = localExecutionNode(binding, userId, traceId);
            AgentSessionBinding agentBinding = ensureAgentSession(
                    resolvedAgentId, runtime, session, workspace, node, traceId);
            return new SessionRuntimeTarget(runtime, node, workspace.rootPath(), agentBinding.remoteSessionId());
        }
        if (localBinding(session.workspaceId()) != null) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地会话运行目标缺失，禁止降级到服务端 OpenCode");
        }
        Optional<UserOpencodeProcessAssignment> userAssignment =
                resolveUserProcessAssignment(userId, resolvedAgentId, traceId);
        if (userAssignment.isPresent()) {
            AgentSessionBinding binding = ensureAgentSession(
                    resolvedAgentId,
                    runtime,
                    session,
                    workspace,
                    userAssignment.get().node(),
                    traceId);
            return new SessionRuntimeTarget(
                    runtime,
                    userAssignment.get().node(),
                    workspaceRoot(workspace),
                    binding.remoteSessionId(),
                    workspace.workspaceId());
        }
        AgentSessionBinding binding = findAgentBinding(resolvedAgentId, session, traceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT,
                        "Session 尚未绑定远端 agent 会话",
                        Map.of("sessionId", sessionId, "agentId", resolvedAgentId)));
        ExecutionNode node = executionNodeRepository.findById(binding.executionNodeId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "会话绑定的 agent 执行节点不存在",
                        Map.of("agentId", resolvedAgentId, "nodeId", binding.executionNodeId().value())));
        return new SessionRuntimeTarget(
                runtime, node, workspaceRoot(workspace), binding.remoteSessionId(), workspace.workspaceId());
    }

    /**
     * 生产环境按用户历史归因校验 ACTIVE 会话；兼容手工构造测试时至少校验显式创建人。
     * 工作区策略随后再次确认体验资格、当前服务器绑定和应用成员关系。
     */
    private void requireAuthenticatedSessionAccess(UserId userId, Session session) {
        if (userId == null) {
            return;
        }
        boolean visible = session.status() == SessionStatus.ACTIVE;
        if (visible && session.createdByUserId() != null) {
            visible = session.createdByUserId().equals(userId);
        } else if (visible && sessionHistoryRepository != null) {
            visible = sessionHistoryRepository.findUserSession(userId, session.sessionId()).isPresent();
        }
        if (!visible) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "Session 不存在",
                    Map.of("sessionId", session.sessionId().value()));
        }
    }

    /** static token 只保留普通固定节点兼容，不得用历史体验 ID 绕过用户资格与服务器绑定。 */
    private void requireRuntimeWorkspaceAccess(UserId userId, WorkspaceId workspaceId) {
        if (userId == null) {
            if (ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspaceId)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "体验工作区必须使用用户身份访问");
            }
            return;
        }
        workspaceAccessAuthorizer.requireAccess(userId, workspaceId);
    }

    /** 旧会话创建人字段可能为空，生产装配使用历史归因端口完成同一用户校验。 */
    @Autowired
    void configureSessionHistoryRepository(SessionHistoryRepository sessionHistoryRepository) {
        this.sessionHistoryRepository = Objects.requireNonNull(
                sessionHistoryRepository, "sessionHistoryRepository must not be null");
    }

    /**
     * 仅按平台 Session 已持久化的 agent 映射还原原执行目标，不读取当前用户进程 binding，
     * 用于重启后的内部会话清理，避免把 DELETE 错发到用户后来迁移到的新节点。
     */
    public SessionRuntimeTarget mappedSideQuestionSessionTarget(String agentId, SessionId sessionId, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        Session session = findSession(sessionId);
        if (session.status() != SessionStatus.ARCHIVED
                || session.sourceType() != ConversationSourceType.SIDE_QUESTION) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Session 不是已归档的旁路内部会话",
                    Map.of("sessionId", sessionId.value()));
        }
        Workspace workspace = findWorkspace(session.workspaceId());
        AgentSessionBinding binding = findAgentBinding(resolvedAgentId, session, traceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT,
                        "Session 尚未保存远端 agent 会话映射",
                        Map.of(
                                "sessionId", sessionId.value(),
                                "agentId", resolvedAgentId,
                                "reason", "REMOTE_SESSION_MAPPING_MISSING")));
        com.enterprise.testagent.domain.session.SessionRuntimeTarget frozenTarget = sessionRuntimeTarget(session);
        if (frozenTarget.runtimeKind() == RuntimeKind.LOCAL_CLIENT) {
            LocalClientWorkspaceBinding localBinding = requireFrozenLocalBinding(session, frozenTarget);
            return new SessionRuntimeTarget(
                    runtime,
                    localExecutionNode(localBinding, null, traceId),
                    workspace.rootPath(),
                    binding.remoteSessionId());
        }
        ExecutionNode node = executionNodeRepository.findById(binding.executionNodeId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "会话映射的 agent 执行节点不存在",
                        Map.of("agentId", resolvedAgentId, "nodeId", binding.executionNodeId().value())));
        return new SessionRuntimeTarget(
                runtime, node, workspaceRoot(workspace), binding.remoteSessionId(), workspace.workspaceId());
    }

    /**
     * 解析用户专属 opencode 进程；非认证、非默认 opencode 时返回空以保留旧固定节点链路。
     */
    public Optional<UserOpencodeProcessAssignment> resolveUserProcessAssignment(
            UserId userId,
            String agentId,
            String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        if (userId == null || !isDefaultOpencode(resolvedAgentId)) {
            return Optional.empty();
        }
        if (userProcessAssignmentService == null) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "用户 opencode 进程管理未启用");
        }
        return Optional.of(userProcessAssignmentService.requireReadyProcess(userId, resolvedAgentId, traceId));
    }

    /** 生产环境装配本地工作区/session 冻结目标与精确 Java 路由；旧测试构造器保持不变。 */
    @Autowired(required = false)
    void configureLocalClientRuntime(
            LocalClientWorkspaceRepository localClientWorkspaceRepository,
            LocalClientConnectionStore localClientConnectionStore,
            SessionRuntimeTargetRepository sessionRuntimeTargetRepository,
            BackendJavaRouteResolver backendJavaRouteResolver) {
        this.localClientWorkspaceRepository = Objects.requireNonNull(
                localClientWorkspaceRepository, "localClientWorkspaceRepository must not be null");
        this.localClientConnectionStore = Objects.requireNonNull(
                localClientConnectionStore, "localClientConnectionStore must not be null");
        this.sessionRuntimeTargetRepository = Objects.requireNonNull(
                sessionRuntimeTargetRepository, "sessionRuntimeTargetRepository must not be null");
        this.backendJavaRouteResolver = Objects.requireNonNull(
                backendJavaRouteResolver, "backendJavaRouteResolver must not be null");
    }

    private com.enterprise.testagent.domain.session.SessionRuntimeTarget sessionRuntimeTarget(Session session) {
        if (sessionRuntimeTargetRepository == null) {
            return com.enterprise.testagent.domain.session.SessionRuntimeTarget.server(session.sessionId());
        }
        return sessionRuntimeTargetRepository.findBySessionId(session.sessionId())
                .orElseGet(() -> com.enterprise.testagent.domain.session.SessionRuntimeTarget.server(session.sessionId()));
    }

    private LocalClientWorkspaceBinding requireFrozenLocalBinding(
            Session session,
            com.enterprise.testagent.domain.session.SessionRuntimeTarget target) {
        LocalClientWorkspaceBinding binding = localBinding(session.workspaceId());
        if (binding == null || !binding.clientInstanceId().equals(target.localClientInstanceId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地会话与工作区客户端绑定不一致");
        }
        return binding;
    }

    private LocalClientWorkspaceBinding localBinding(WorkspaceId workspaceId) {
        return localClientWorkspaceRepository == null
                ? null
                : localClientWorkspaceRepository.findByWorkspaceId(workspaceId).orElse(null);
    }

    private ExecutionNode localExecutionNode(
            LocalClientWorkspaceBinding binding,
            UserId userId,
            String traceId) {
        if (userId != null && !binding.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地工作区不属于当前用户");
        }
        if (localClientConnectionStore == null || backendJavaRouteResolver == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "本地客户端运行路由未装配");
        }
        LocalClientConnectionRoute route = localClientConnectionStore.find(binding.clientInstanceId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "本地客户端离线",
                        Map.of("clientInstanceId", binding.clientInstanceId().value())));
        if (!route.userId().equals(binding.userId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端连接归属不一致");
        }
        if (!backendJavaRouteResolver.isCurrent(route.backendProcessId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "请求未路由到本地客户端连接持有 Java",
                    Map.of("backendProcessId", route.backendProcessId().value()));
        }
        if (route.processStatus() != LocalClientProcessStatus.RUNNING || !route.opencodeHealthy()) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "本地 OpenCode 尚未就绪");
        }
        Instant observedAt = route.lastHeartbeatAt();
        ExecutionNodeId nodeId = new ExecutionNodeId(
                "node_local_" + binding.clientInstanceId().value().substring("lci_".length()));
        persistLocalNodeAnchor(nodeId, route, traceId);
        return new ExecutionNode(
                nodeId,
                "http://local-opencode-client.invalid",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                observedAt,
                Set.of("opencode", "local-client", "file-management"),
                route.connectedAt(),
                observedAt,
                traceId,
                RuntimeKind.LOCAL_CLIENT,
                binding.clientInstanceId().value(),
                route.connectionGeneration());
    }

    /**
     * agent session binding、Session 旧映射和 routing decision 仍外键引用 execution_nodes。
     * 保存 OFFLINE 锚点只用于关系完整性，不能被全局节点路由选中；真正本地目标始终来自冻结记录和 Redis generation。
     */
    private synchronized void persistLocalNodeAnchor(
            ExecutionNodeId nodeId,
            LocalClientConnectionRoute route,
            String traceId) {
        Instant observedAt = route.lastHeartbeatAt();
        executionNodeRepository.save(new ExecutionNode(
                nodeId,
                "http://local-opencode-client.invalid",
                ExecutionNodeStatus.OFFLINE,
                0,
                1,
                0,
                observedAt,
                Set.of("local-client-anchor"),
                route.connectedAt(),
                observedAt,
                traceId));
    }

    /**
     * 确保平台 Session 在指定节点上存在远端 agent session；节点不一致时覆盖当前绑定。
     */
    public AgentSessionBinding ensureAgentSession(
            String agentId,
            AgentRuntime runtime,
            Session session,
            Workspace workspace,
            ExecutionNode node,
            String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        Optional<AgentSessionBinding> existing = findAgentBinding(resolvedAgentId, session, traceId);
        if (existing.isPresent() && existing.get().executionNodeId().equals(node.executionNodeId())) {
            if (remoteSessionAvailable(runtime, node, existing.get(), traceId)) {
                return existing.get();
            }
            LOGGER.warn(
                    "agent_remote_session_missing_recreate traceId={} sessionId={} agentId={} nodeId={} remoteSessionId={}",
                    traceId,
                    session.sessionId().value(),
                    resolvedAgentId,
                    node.executionNodeId().value(),
                    existing.get().remoteSessionId());
        }
        // 首次或用户进程迁移后才创建远端 session；旧远端 session 保留给 opencode 自身清理。
        AgentCreateSessionResult created;
        try {
            created = runtime.createSession(new AgentCreateSessionCommand(
                            node,
                            workspaceRoot(workspace),
                            null,
                            null,
                            traceId))
                    .block();
        } catch (RuntimeException exception) {
            if (!ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspace.workspaceId())) {
                throw exception;
            }
            ErrorCode errorCode = exception instanceof PlatformException platformException
                    ? platformException.errorCode()
                    : ErrorCode.OPENCODE_BAD_GATEWAY;
            // 上游创建请求 URI 携带 directory；体验路径失败时不得把原 message/details/cause 带入响应或日志链。
            LOGGER.warn(
                    "experience_remote_session_create_failed traceId={} sessionId={} agentId={} nodeId={} failureType={}",
                    traceId,
                    session.sessionId().value(),
                    resolvedAgentId,
                    node.executionNodeId().value(),
                    exception.getClass().getSimpleName());
            throw new PlatformException(
                    errorCode,
                    "体验工作区远端会话创建失败",
                    Map.of(
                            "sessionId", session.sessionId().value(),
                            "agentId", resolvedAgentId,
                            "nodeId", node.executionNodeId().value(),
                            "reason", "REMOTE_SESSION_CREATE_FAILED"));
        }
        if (created == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "agent 创建会话未返回结果",
                    Map.of(
                            "sessionId", session.sessionId().value(),
                            "agentId", resolvedAgentId,
                            "nodeId", node.executionNodeId().value()));
        }
        Instant now = Instant.now();
        AgentSessionBinding binding = agentSessionBindingRepository.save(new AgentSessionBinding(
                session.sessionId(),
                resolvedAgentId,
                created.remoteSessionId(),
                node.executionNodeId(),
                existing.map(AgentSessionBinding::createdAt).orElse(now),
                now,
                traceId));
        if (isDefaultOpencode(resolvedAgentId)) {
            sessionRepository.attachOpencodeSession(
                            session.sessionId(),
                            created.remoteSessionId(),
                            node.executionNodeId(),
                            now,
                            traceId)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.NOT_FOUND,
                            "Session 不存在",
                            Map.of("sessionId", session.sessionId().value())));
        }
        return binding;
    }

    private boolean remoteSessionAvailable(
            AgentRuntime runtime,
            ExecutionNode node,
            AgentSessionBinding binding,
            String traceId) {
        // 只在即将复用同节点绑定时探测远端；404 缺失交给上层重建绑定，其他错误继续抛出。
        Boolean exists = runtime.sessionExists(new AgentSessionExistsCommand(
                        node,
                        binding.remoteSessionId(),
                        traceId))
                .block();
        if (exists == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "agent session 校验未返回结果",
                    Map.of(
                            "sessionId", binding.sessionId().value(),
                            "agentId", binding.agentId(),
                            "nodeId", node.executionNodeId().value()));
        }
        return exists;
    }

    /**
     * 查询通用 agent 绑定；opencode 旧字段只作为兼容回填来源，不进入前端契约。
     */
    public Optional<AgentSessionBinding> findAgentBinding(String agentId, Session session, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        Optional<AgentSessionBinding> binding =
                agentSessionBindingRepository.findBySessionIdAndAgentId(session.sessionId(), resolvedAgentId);
        if (binding.isPresent()) {
            return binding;
        }
        if (isDefaultOpencode(resolvedAgentId) && session.hasOpencodeSessionMapping()) {
            AgentSessionBinding legacy = new AgentSessionBinding(
                    session.sessionId(),
                    resolvedAgentId,
                    session.opencodeSessionId(),
                    session.opencodeExecutionNodeId(),
                    session.createdAt(),
                    session.updatedAt(),
                    traceId);
            return Optional.of(agentSessionBindingRepository.save(legacy));
        }
        return Optional.empty();
    }

    private Session findSession(SessionId sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Session 不存在",
                        Map.of("sessionId", sessionId.value())));
    }

    private Workspace findWorkspace(WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Workspace 不存在",
                        Map.of("workspaceId", workspaceId.value())));
    }

    private ExecutionNode routableNode() {
        return executionNodeRepository.findRoutableNodes(1).stream()
                .findFirst()
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "没有可用 opencode 执行节点"));
    }

    private boolean isDefaultOpencode(String agentId) {
        return AgentRuntimeRegistry.DEFAULT_AGENT_ID.equals(agentRuntimeRegistry.normalize(agentId));
    }

    private String workspaceRoot(Workspace workspace) {
        return pathResolver.resolve(workspace.rootPath()).toString();
    }

    /**
     * workspace/session runtime 调用的公共目标视图。
     */
    public interface RuntimeTarget {

        /**
         * 返回目标 agent runtime。
         */
        AgentRuntime runtime();

        /**
         * 返回目标执行节点。
         */
        ExecutionNode node();

        /**
         * 返回要传给 opencode 的 directory。
         */
        String directory();

        /** 返回该调用绑定的平台 Workspace；全局 runtime 调用为空。 */
        WorkspaceId workspaceId();
    }

    /**
     * workspace 级 runtime 调用目标。
     */
    public record WorkspaceRuntimeTarget(
            AgentRuntime runtime,
            ExecutionNode node,
            String directory,
            WorkspaceId workspaceId)
            implements RuntimeTarget {

        /** 兼容不需要 Workspace 分类的既有内部测试。 */
        public WorkspaceRuntimeTarget(AgentRuntime runtime, ExecutionNode node, String directory) {
            this(runtime, node, directory, null);
        }
    }

    /**
     * session 级 runtime 调用目标，包含已解析的远端 session id。
     */
    public record SessionRuntimeTarget(
            AgentRuntime runtime,
            ExecutionNode node,
            String directory,
            String remoteSessionId,
            WorkspaceId workspaceId)
            implements RuntimeTarget {

        /** 兼容历史清理与旁路会话测试的手工目标。 */
        public SessionRuntimeTarget(
                AgentRuntime runtime,
                ExecutionNode node,
                String directory,
                String remoteSessionId) {
            this(runtime, node, directory, remoteSessionId, null);
        }
    }
}
