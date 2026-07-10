package com.icbc.testagent.opencode.runtime.runtime;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.agent.runtime.AgentCreateSessionCommand;
import com.icbc.testagent.agent.runtime.AgentCreateSessionResult;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentSessionExistsCommand;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            ManagedWorkspacePathResolver pathResolver) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.agentSessionBindingRepository = Objects.requireNonNull(agentSessionBindingRepository, "agentSessionBindingRepository must not be null");
        this.userProcessAssignmentService = userProcessAssignmentService;
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
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
        ExecutionNode node = resolveUserProcessAssignment(userId, resolvedAgentId, traceId)
                .map(UserOpencodeProcessAssignment::node)
                .orElseGet(this::routableNode);
        if (workspaceId == null || workspaceId.isBlank()) {
            return new WorkspaceRuntimeTarget(runtime, node, null);
        }
        Workspace workspace = findWorkspace(new WorkspaceId(workspaceId));
        return new WorkspaceRuntimeTarget(runtime, node, workspaceRoot(workspace));
    }

    /**
     * 解析 session 级 runtime 调用目标；认证 opencode 用户在节点不一致时自动重建远端 session。
     */
    public SessionRuntimeTarget sessionTarget(String agentId, UserId userId, String sessionId, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        Session session = findSession(new SessionId(sessionId));
        Workspace workspace = findWorkspace(session.workspaceId());
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
                    binding.remoteSessionId());
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
        return new SessionRuntimeTarget(runtime, node, workspaceRoot(workspace), binding.remoteSessionId());
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
        AgentCreateSessionResult created = runtime.createSession(new AgentCreateSessionCommand(
                        node,
                        workspaceRoot(workspace),
                        null,
                        null,
                        traceId))
                .block();
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
    }

    /**
     * workspace 级 runtime 调用目标。
     */
    public record WorkspaceRuntimeTarget(AgentRuntime runtime, ExecutionNode node, String directory)
            implements RuntimeTarget {
    }

    /**
     * session 级 runtime 调用目标，包含已解析的远端 session id。
     */
    public record SessionRuntimeTarget(AgentRuntime runtime, ExecutionNode node, String directory, String remoteSessionId)
            implements RuntimeTarget {
    }
}
