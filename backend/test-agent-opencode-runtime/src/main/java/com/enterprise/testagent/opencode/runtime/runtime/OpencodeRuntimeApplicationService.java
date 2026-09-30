package com.enterprise.testagent.opencode.runtime.runtime;

import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.agent.runtime.AgentSessionMessage;
import com.enterprise.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.enterprise.testagent.agent.runtime.AgentSessionMessagesResult;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigMessageGate;
import com.enterprise.testagent.domain.hub.ProtectedAgentDefinitionResolver;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityRepository;
import com.enterprise.testagent.opencode.runtime.model.ModelCatalogApplicationService;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionSessionLockGuard;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.session.SessionApplicationService;
import com.enterprise.testagent.opencode.runtime.session.UserRuntimeDisposeCoordinator;
import com.enterprise.testagent.opencode.runtime.support.ExperienceWorkspacePathRedactor;
import com.enterprise.testagent.opencode.client.OpencodeV2FormAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * opencode Web App 运行态 API 编排层，统一把平台请求映射到 AgentRuntime。
 */
@Service
public class OpencodeRuntimeApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpencodeRuntimeApplicationService.class);
    private static final SideQuestionAnswerExtractor SIDE_QUESTION_ANSWER_EXTRACTOR = new SideQuestionAnswerExtractor();

    private final AgentRuntimeRegistry agentRuntimeRegistry;
    private final AgentRuntimeTargetResolver targetResolver;
    private final ObjectMapper objectMapper;
    private final ModelCatalogApplicationService modelCatalogService;
    private final RunApplicationService runApplicationService;
    private UserRuntimeDisposeCoordinator userRuntimeDisposeCoordinator;
    private NightExecutionSessionLockGuard sessionLockGuard;
    private SessionApplicationService sessionApplicationService;
    private PublicAgentConfigMessageGate publicConfigMessageGate = ignored ->
            PublicAgentConfigMessageGate.MessageGateStatus.open();
    private ProtectedAgentDefinitionResolver protectedAgentDefinitionResolver;
    private LocalClientInstanceRepository localClientInstanceRepository;
    private LocalClientPublicCapabilityRepository localClientPublicCapabilityRepository;
    private final ThreadLocal<String> agentContext = new ThreadLocal<>();
    private final ThreadLocal<UserId> userContext = new ThreadLocal<>();

    /**
     * 兼容旧测试和手工装配；生产环境使用下方注入 Run 服务的构造器。
     */
    public OpencodeRuntimeApplicationService(
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentRuntimeTargetResolver targetResolver,
            ObjectMapper objectMapper,
            ModelCatalogApplicationService modelCatalogService) {
        this(agentRuntimeRegistry, targetResolver, objectMapper, modelCatalogService, null);
    }

    /**
     * 生产装配额外注入 Run 服务，用于 ask 回复后在远端最终消息明确完成时收敛平台 Run。
     */
    @Autowired
    public OpencodeRuntimeApplicationService(
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentRuntimeTargetResolver targetResolver,
            ObjectMapper objectMapper,
            ModelCatalogApplicationService modelCatalogService,
            RunApplicationService runApplicationService) {
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.targetResolver = Objects.requireNonNull(targetResolver, "targetResolver must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.modelCatalogService = modelCatalogService;
        this.runApplicationService = runApplicationService;
    }

    /** 兼容旧 side-question HTTP 入口，但发送前必须执行与新 Run 相同的用户级门禁。 */
    @Autowired
    void configurePublicConfigMessageGate(PublicAgentConfigMessageGate messageGate) {
        this.publicConfigMessageGate = Objects.requireNonNull(messageGate, "messageGate must not be null");
    }

    /** 所有用户级 OpenCode dispose 入口共用同一套跨 Session 空闲闸门。 */
    @Autowired(required = false)
    void configureUserRuntimeDisposeCoordinator(UserRuntimeDisposeCoordinator coordinator) {
        this.userRuntimeDisposeCoordinator = Objects.requireNonNull(
                coordinator, "coordinator must not be null");
    }

    /** 等待原生回退期间，所有可改变主会话的兼容写入口复用同一数据库锁。 */
    @Autowired(required = false)
    void configureSessionLockGuard(NightExecutionSessionLockGuard lockGuard) {
        this.sessionLockGuard = Objects.requireNonNull(lockGuard, "lockGuard must not be null");
    }

    /** compact 成功后推进平台会话修订，供分享状态流通知所有参与者刷新远端消息快照。 */
    @Autowired(required = false)
    void configureSessionApplicationService(SessionApplicationService service) {
        this.sessionApplicationService = Objects.requireNonNull(service, "service must not be null");
    }

    /** Hub 只向本地工作区目录追加受保护选择句柄，正文仍留在服务器。 */
    @Autowired(required = false)
    void configureProtectedAgentDefinitionResolver(ProtectedAgentDefinitionResolver resolver) {
        this.protectedAgentDefinitionResolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /** 新客户端本地已安装公共 Git Agent 时，只过滤同源受保护目录；应用 Hub 资产继续保留。 */
    @Autowired(required = false)
    void configureLocalClientInstanceRepository(LocalClientInstanceRepository repository) {
        this.localClientInstanceRepository = Objects.requireNonNull(repository);
    }

    @Autowired(required = false)
    void configureLocalClientPublicCapabilityRepository(LocalClientPublicCapabilityRepository repository) {
        this.localClientPublicCapabilityRepository = Objects.requireNonNull(repository);
    }

    /**
     * 创建兼容旧测试的服务实例，不启用用户进程上下文。
     */
    public OpencodeRuntimeApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            ObjectMapper objectMapper,
            ModelCatalogApplicationService modelCatalogService) {
        this(
                agentRuntimeRegistry,
                new AgentRuntimeTargetResolver(
                        workspaceRepository,
                        sessionRepository,
                        executionNodeRepository,
                        agentRuntimeRegistry,
                        agentSessionBindingRepository,
                        null),
                objectMapper,
                Objects.requireNonNull(modelCatalogService, "modelCatalogService must not be null"));
    }

    /**
     * 兼容旧单测的构造器，默认保持 opencode 原始模型/provider 代理。
     */
    public OpencodeRuntimeApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            ObjectMapper objectMapper) {
        this(
                agentRuntimeRegistry,
                new AgentRuntimeTargetResolver(
                        workspaceRepository,
                        sessionRepository,
                        executionNodeRepository,
                        agentRuntimeRegistry,
                        agentSessionBindingRepository,
                        null),
                objectMapper,
                null);
    }

    /**
     * 创建启用用户进程服务的测试实例，复用生产目标解析规则。
     */
    public OpencodeRuntimeApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            ObjectMapper objectMapper,
            UserOpencodeProcessAssignmentService userProcessAssignmentService) {
        this(
                agentRuntimeRegistry,
                new AgentRuntimeTargetResolver(
                        workspaceRepository,
                        sessionRepository,
                        executionNodeRepository,
                        agentRuntimeRegistry,
                        agentSessionBindingRepository,
                        userProcessAssignmentService),
                objectMapper,
                null);
    }

    /**
     * 在一次同步 runtime 代理调用中指定 agentId，旧 Controller 不调用该方法时默认 opencode。
     */
    public <T> T withAgent(String agentId, Supplier<T> supplier) {
        return withAgent(agentId, null, supplier);
    }

    /**
     * 在一次同步 runtime 代理调用中同时指定 agentId 和当前用户；用户为空时保留旧固定节点兼容。
     */
    public <T> T withAgent(String agentId, UserId userId, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        String previous = agentContext.get();
        UserId previousUser = userContext.get();
        agentContext.set(agentRuntimeRegistry.normalize(agentId));
        setUserContext(userId);
        try {
            return supplier.get();
        } finally {
            restoreAgentContext(previous);
            restoreUserContext(previousUser);
        }
    }

    /**
     * 在旧平台 runtime 入口中指定可选认证用户；没有用户时保持 static-token/fallback 行为。
     */
    public <T> T withUser(UserId userId, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        UserId previousUser = userContext.get();
        setUserContext(userId);
        try {
            return supplier.get();
        } finally {
            restoreUserContext(previousUser);
        }
    }

    /**
     * 列出当前 workspace 可用 agent。
     */
    public Object listAgents(String workspaceId, String traceId) {
        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location = workspaceLocation(workspaceId, traceId);
        Object nativeCatalog = get(location, "/agent", Map.of(), traceId);
        if (nativeCatalog instanceof Map<?, ?> envelope && envelope.get("data") instanceof List<?> items) {
            // V2 agent 目录带 location envelope；受保护 Agent 合并必须以真实目录数组为底。
            nativeCatalog = items;
        }
        UserId userId = userContext.get();
        if (protectedAgentDefinitionResolver == null
                || userId == null
                || location.workspaceId() == null
                || location.node().runtimeKind() != RuntimeKind.LOCAL_CLIENT) {
            return nativeCatalog;
        }
        List<Object> merged = new java.util.ArrayList<>();
        if (nativeCatalog instanceof List<?> nativeItems) {
            merged.addAll(nativeItems);
        }
        boolean publicGitInstalled = supportsPublicCapability(location);
        protectedAgentDefinitionResolver.listCatalog(userId, location.workspaceId()).stream()
                .filter(item -> !publicGitInstalled
                        || item.source() != ProtectedAgentDefinitionResolver.CatalogSource.PUBLIC_GIT)
                .map(item -> {
                    Map<String, Object> projected = new LinkedHashMap<>();
                    projected.put("id", item.selectionId());
                    projected.put("agentId", item.selectionId());
                    projected.put("name", item.displayName());
                    projected.put("mode", "primary");
                    projected.put("description", item.description() == null || item.description().isBlank()
                            ? "平台受保护 Agent（服务器执行）"
                            : item.description());
                    projected.put("protected", true);
                    projected.put("source", item.source().name());
                    projected.put("revisionId", item.revisionId());
                    projected.put("contentSha256", item.contentSha256());
                    return Map.copyOf(projected);
                })
                .forEach(merged::add);
        return List.copyOf(merged);
    }

    private boolean supportsPublicCapability(AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location) {
        if (localClientInstanceRepository == null
                || localClientPublicCapabilityRepository == null
                || location.node().localClientInstanceId() == null) {
            return false;
        }
        try {
            LocalClientInstanceId instanceId = new LocalClientInstanceId(location.node().localClientInstanceId());
            boolean supported = localClientInstanceRepository.findById(instanceId)
                    .map(instance -> instance.selfUpdateCapabilities().contains("PUBLIC_CAPABILITY_SYNC_V1"))
                    .orElse(false);
            return supported && localClientPublicCapabilityRepository.findInstanceState(instanceId)
                    .map(state -> state.activeDigest() != null)
                    .orElse(false);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 列出当前 workspace 可用模型；模型目录始终以 opencode 原生配置文件为准。
     */
    public Object listModels(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/api/model", Map.of(), traceId);
    }

    /**
     * 列出当前 workspace 可用 provider；供应商目录始终以 opencode 原生配置文件为准。
     */
    public Object listProviders(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/api/provider", Map.of(), traceId);
    }

    /**
     * 列出 opencode command catalog。
     */
    public Object listCommands(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/command", Map.of(), traceId);
    }

    /**
     * 列出 opencode reference catalog。
     */
    public Object listReferences(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/api/reference", Map.of(), traceId);
    }

    /**
     * 查询 opencode runtime 健康状态，兼容 Web App 原始 /api/status 请求。
     */
    public Object runtimeStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/api/info", Map.of(), traceId);
    }

    /**
     * 读取 opencode 文件列表，path 缺省时使用当前目录。
     */
    public Object fsList(String workspaceId, String path, String traceId) {
        return get(runtimeFileLocation(workspaceId, traceId), "/file", query("path", path == null || path.isBlank() ? "." : path), traceId);
    }

    /**
     * 调用 opencode 文件搜索 API。
     */
    public Object fsFind(String workspaceId, String query, String traceId) {
        return get(runtimeFileLocation(workspaceId, traceId), "/find/file", query("query", query), traceId);
    }

    /**
     * 读取 opencode workspace 文件内容。
     */
    public Object fsRead(String workspaceId, String path, String traceId) {
        return get(runtimeFileLocation(workspaceId, traceId), "/file/content", query("path", path), traceId);
    }

    /**
     * 读取远端 VCS 状态。
     */
    public Object vcsStatus(String workspaceId, String traceId) {
        return get(runtimeVcsLocation(workspaceId, traceId), "/vcs/status", Map.of(), traceId);
    }

    /**
     * 读取远端 VCS Diff；旧平台的 git 模式与 V2 working 均表示相对 HEAD 的工作区改动。
     */
    public Object vcsDiff(String workspaceId, String mode, Integer context, String traceId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("mode", mode == null || mode.isBlank() || "git".equals(mode) ? "working" : mode);
        if (context != null) {
            query.put("context", Integer.toString(context));
        }
        return get(runtimeVcsLocation(workspaceId, traceId), "/vcs/diff", query, traceId);
    }

    /** 体验 Git 只能走平台只读实现，禁止 OpenCode 普通 status/diff 触发 index 或外部 helper。 */
    private AgentRuntimeTargetResolver.RuntimeTarget runtimeVcsLocation(String workspaceId, String traceId) {
        AgentRuntimeTargetResolver.RuntimeTarget location = workspaceLocation(workspaceId, traceId);
        if (experienceRuntimeTarget(location)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "体验工作区不支持该 Git 操作");
        }
        return location;
    }

    /**
     * 查询远端 LSP 状态。
     */
    public Object lspStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/lsp", Map.of(), traceId);
    }

    /**
     * 查询远端 MCP 状态。
     */
    public Object mcpStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/mcp", Map.of(), traceId);
    }

    /**
     * 查询远端 MCP resources。
     */
    public Object mcpResources(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/experimental/resource", Map.of(), traceId);
    }

    /**
     * 查询远端 MCP tools；指定 provider/model 时读取工具详情，否则读取工具 ID 列表。
     */
    public Object mcpTools(String workspaceId, String provider, String model, String traceId) {
        Map<String, String> query = new LinkedHashMap<>();
        if (provider != null && !provider.isBlank() && model != null && !model.isBlank()) {
            query.put("provider", provider);
            query.put("model", model);
            return get(workspaceLocation(workspaceId, traceId), "/experimental/tool", query, traceId);
        }
        return get(workspaceLocation(workspaceId, traceId), "/experimental/tool/ids", Map.of(), traceId);
    }

    /**
     * 读取当前 workspace 合并后的有效配置；模型目录必须从这里取得 OPENCODE_CONFIG_DIR 中的 Provider 白名单。
     */
    public Object getEffectiveConfig(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/config", Map.of(), traceId);
    }

    /**
     * 读取 opencode 全局配置；只供 Agent 标准 global/config 兼容路径使用。
     */
    public Object getConfig(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/api/config", Map.of(), traceId);
    }

    /**
     * V2 仅允许通过实验接口更新 shell；Provider 和模型配置必须走受控配置发布。
     */
    public Object updateConfig(String workspaceId, Map<String, Object> body, String traceId) {
        Map<String, Object> requested = safeBody(body);
        if (requested.size() != 1 || !requested.containsKey("shell")
                || (requested.get("shell") != null && !(requested.get("shell") instanceof String))) {
            throw new PlatformException(ErrorCode.API_GONE, "OpenCode V2 只支持通过 config API 更新 shell 字段");
        }
        return patch(workspaceLocation(workspaceId, traceId), "/api/experimental/config", requested, traceId);
    }

    /**
     * 触发 opencode runtime dispose，用于 Web App 设置页的服务重载能力。
     */
    public Object disposeGlobal(String traceId) {
        UserId userId = currentUserId();
        if (userId == null || userRuntimeDisposeCoordinator == null) {
            return post(workspaceLocation(null, traceId), "/global/dispose", Map.of(), traceId);
        }
        return userRuntimeDisposeCoordinator.withUserIdle(
                userId,
                traceId,
                () -> post(workspaceLocation(null, traceId), "/global/dispose", Map.of(), traceId));
    }

    /**
     * 查询 provider auth 状态。
     */
    public Object listProviderAuth(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/provider/auth", Map.of(), traceId);
    }

    /**
     * 发起 provider OAuth 授权。
     */
    public Object authorizeProviderOAuth(String providerId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location = workspaceLocation(null, traceId);
        Map<String, Object> source = safeBody(body);
        String methodId = text(source.get("methodID"));
        if (methodId == null) {
            Object integration = get(location, "/provider/" + encodePath(providerId), Map.of(), traceId);
            Object data = integration instanceof Map<?, ?> envelope ? envelope.get("data") : null;
            Object methods = data instanceof Map<?, ?> info ? info.get("methods") : null;
            if (methods instanceof List<?> entries) {
                for (Object entry : entries) {
                    if (entry instanceof Map<?, ?> method && "oauth".equals(method.get("type"))) {
                        methodId = text(method.get("id"));
                        if (methodId != null) break;
                    }
                }
            }
        }
        if (methodId == null) {
            throw new PlatformException(ErrorCode.API_GONE, "OpenCode V2 集成没有可用的 OAuth 授权方法");
        }
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("methodID", methodId);
        if (source.get("answer") instanceof Map<?, ?> answer) request.put("answer", answer);
        if (source.get("label") instanceof String label) request.put("label", label);
        return post(location, "/provider/" + encodePath(providerId) + "/oauth/authorize", request, traceId);
    }

    /**
     * 完成 provider OAuth 回调。
     */
    public Object completeProviderOAuth(String providerId, Map<String, Object> body, String traceId) {
        Map<String, Object> source = safeBody(body);
        String attemptId = text(source.get("attemptID"));
        if (attemptId == null) attemptId = text(source.get("attemptId"));
        if (attemptId == null) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "V2 OAuth 回调缺少 attemptID");
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("attemptID", attemptId);
        if (source.get("code") instanceof String code) request.put("code", code);
        return post(workspaceLocation(null, traceId),
                "/provider/" + encodePath(providerId) + "/oauth/callback", request, traceId);
    }

    /**
     * 写入 provider auth secret，secret 不在应用层记录日志或持久化。
     */
    public Object setProviderAuth(String providerId, Map<String, Object> body, String traceId) {
        Map<String, Object> source = safeBody(body);
        String key = text(source.get("key"));
        if (key == null) key = text(source.get("token"));
        if (key == null) key = text(source.get("apiKey"));
        if (key == null) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "V2 provider auth 缺少 key");
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("key", key);
        if (source.get("answer") instanceof Map<?, ?> answer) request.put("answer", answer);
        if (source.get("label") instanceof String label) request.put("label", label);
        return post(workspaceLocation(null, traceId), "/auth/" + encodePath(providerId), request, traceId);
    }

    /**
     * 删除 provider auth secret。
     */
    public Object removeProviderAuth(String providerId, String traceId) {
        throw new PlatformException(
                ErrorCode.API_GONE,
                "OpenCode V2 未提供删除 provider auth 的原生接口，请在 provider 配置中移除凭据");
    }

    /**
     * 查询 opencode experimental worktree 列表。
     */
    public Object listWorktrees(String workspaceId, String traceId) {
        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location = nonExperienceWorkspaceLocation(workspaceId, traceId);
        return get(location, "/experimental/worktree", Map.of("projectID", opencodeProjectId(location, traceId)), traceId);
    }

    /**
     * 创建 worktree；workspaceId 只用于平台路由，不透传为额外策略。
     */
    public Object createWorktree(Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location =
                nonExperienceWorkspaceLocation(requiredWorktreeWorkspaceId(body), traceId);
        Map<String, Object> forwarded = worktreeBody(body);
        forwarded.put("projectID", opencodeProjectId(location, traceId));
        return post(
                location,
                "/experimental/worktree",
                forwarded,
                traceId);
    }

    /**
     * 删除 worktree。
     */
    public Object removeWorktree(Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location =
                nonExperienceWorkspaceLocation(requiredWorktreeWorkspaceId(body), traceId);
        Map<String, Object> forwarded = worktreeBody(body);
        String projectId = opencodeProjectId(location, traceId);
        requireListedWorktree(location, projectId, requiredWorktreeDirectory(forwarded), traceId);
        forwarded.put("projectID", projectId);
        forwarded.putIfAbsent("force", false);
        return delete(
                location,
                "/experimental/worktree",
                forwarded,
                traceId);
    }

    /**
     * 重置 worktree。
     */
    public Object resetWorktree(Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location =
                nonExperienceWorkspaceLocation(requiredWorktreeWorkspaceId(body), traceId);
        Map<String, Object> forwarded = worktreeBody(body);
        String projectId = opencodeProjectId(location, traceId);
        requireListedWorktree(location, projectId, requiredWorktreeDirectory(forwarded), traceId);
        return post(
                location,
                "/experimental/worktree/reset",
                Map.of("projectID", projectId),
                traceId);
    }

    /**
     * 查询远端 session children，平台 sessionId 会先映射为 opencode session id。
     */
    public Object sessionChildren(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        // V2 removes the session-scoped /children route; the list endpoint accepts parentID instead.
        return get(location, "/session", Map.of("parentID", location.remoteSessionId()), traceId);
    }

    /**
     * 查询远端 session todo。
     */
    public Object sessionTodo(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        AgentSessionMessagesResult messages = location.runtime().sessionMessages(new AgentSessionMessagesCommand(
                        location.node(),
                        location.remoteSessionId(),
                        200,
                        "desc",
                        null,
                        traceId))
                .block();
        List<Map<String, Object>> latest = List.of();
        if (messages != null) {
            for (AgentSessionMessage message : messages.messages()) {
                if (!"assistant".equalsIgnoreCase(text(message.message().get("role")))) {
                    continue;
                }
                for (Map<String, Object> part : message.parts()) {
                    List<Map<String, Object>> snapshot = todoSnapshotFromPart(part);
                    if (snapshot != null) {
                        latest = snapshot;
                    }
                }
            }
        }
        // V2 没有旧的 session/{id}/todo endpoint；从消息中的 todowrite part 恢复平台 Todo。
        return Map.of("data", latest);
    }

    /**
     * 查询远端 session Diff；平台 messageId 表示 V2 的 USER 轮次锚点，映射为 from。
     */
    public Object sessionDiff(String sessionId, String messageId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return get(location, "/session/" + encodePath(location.remoteSessionId()) + "/diff", query("from", messageId), traceId);
    }

    /**
     * 请求远端中止 session。
     */
    public Object abortSession(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/abort", Map.of(), traceId);
    }

    /**
     * 请求远端 fork session，body 透传已由 API 层完成输入约束。
     */
    public Object forkSession(String sessionId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/fork", safeBody(body), traceId);
    }

    /**
     * 在临时 fork 中执行一次旁路问答：先按消息数/文本量判断是否需要压缩，再发送单条消息，最后删除临时会话。
     * 主会话不会追加旁路问题；压缩也只作用于临时 fork，避免改变用户正在进行的主上下文。
     */
    public SideQuestionResult sideQuestion(String sessionId, SideQuestionInput input, String traceId) {
        Objects.requireNonNull(input, "input must not be null");
        requireNewMessageAllowed(traceId);
        String question = SideQuestionPolicy.requireQuestion(input.question());

        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        AgentSessionMessagesResult context = location.runtime().sessionMessages(new AgentSessionMessagesCommand(
                        location.node(),
                        location.remoteSessionId(),
                        SideQuestionPolicy.CONTEXT_MESSAGE_LIMIT + 1,
                        "desc",
                        null,
                        traceId))
                .block();
        boolean shouldCompact = SideQuestionPolicy.shouldCompact(context);

        LinkedHashMap<String, Object> forkBody = new LinkedHashMap<>();
        if (input.messageId() != null) {
            forkBody.put("messageID", input.messageId());
        }
        Object forkResponse = post(
                location,
                "/session/" + encodePath(location.remoteSessionId()) + "/fork",
                forkBody,
                traceId);
        String temporarySessionId = extractSessionId(forkResponse);
        if (temporarySessionId == null) {
            throw new IllegalStateException("opencode fork response did not contain a session id");
        }

        boolean compacted = false;
        try {
            ModelSelection model = parseModel(input.model());
            String temporaryPath = "/session/" + encodePath(temporarySessionId);
            if (input.agent() != null) {
                post(location, temporaryPath + "/agent", Map.of("agent", input.agent()), traceId);
            }
            if (model != null) {
                post(location, temporaryPath + "/model", Map.of("model", Map.of(
                        "providerID", model.providerId(), "id", model.modelId())), traceId);
            }
            if (shouldCompact) {
                if (model == null) {
                    throw new IllegalArgumentException("side question requires model provider/model when context compaction is needed");
                }
                post(
                        location,
                        temporaryPath + "/summarize",
                        Map.of(),
                        traceId);
                waitForSideQuestionSession(location, temporaryPath, traceId);
                compacted = true;
            }
            // V2 prompt 只接受 text；先固定临时 session 的 agent/model，再把只读约束加入用户问题。
            post(location, temporaryPath + "/prompt",
                    Map.of("text", SideQuestionPolicy.SYSTEM_PROMPT + "\n\n" + question), traceId);
            waitForSideQuestionSession(location, temporaryPath, traceId);
            Object messages = get(location, temporaryPath + "/message",
                    Map.of("order", "desc", "limit", "40"), traceId);
            String answer = latestSideQuestionAnswer(messages);
            if (answer == null) {
                throw new IllegalStateException("opencode side question response did not contain a natural-language answer");
            }
            return new SideQuestionResult(answer, compacted);
        } finally {
            try {
                delete(location, "/session/" + encodePath(temporarySessionId), Map.of(), traceId);
            } catch (RuntimeException cleanupFailure) {
                LOGGER.warn(
                        "event=opencode_side_question_cleanup_failed traceId={} sessionId={} error={}",
                        traceId,
                        temporarySessionId,
                        cleanupFailure.getClass().getSimpleName());
            }
        }
    }

    /** V2 wait 对已经空闲的临时会话可能返回 404，随后仍需从消息快照确认最终答案。 */
    private void waitForSideQuestionSession(
            AgentRuntimeTargetResolver.SessionRuntimeTarget location,
            String temporaryPath,
            String traceId) {
        try {
            post(location, temporaryPath + "/wait", Map.of(), traceId);
        } catch (PlatformException exception) {
            if (!Integer.valueOf(404).equals(exception.details().get("status"))) throw exception;
        }
    }

    /** V2 消息按倒序读取，第一条 assistant 才是刚完成的回答，不能回退到 fork 的历史回答。 */
    private String latestSideQuestionAnswer(Object response) {
        Object items = response instanceof Map<?, ?> envelope ? envelope.get("data") : response;
        if (!(items instanceof List<?> list)) return SIDE_QUESTION_ANSWER_EXTRACTOR.extract(response);
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> message)) continue;
            Object info = message.get("info") instanceof Map<?, ?> nested ? nested : message;
            if (!(info instanceof Map<?, ?> map)) continue;
            if (!"assistant".equals(text(map.get("role"))) && !"assistant".equals(text(map.get("type")))) continue;
            return SIDE_QUESTION_ANSWER_EXTRACTOR.extract(List.of(item));
        }
        return null;
    }

    /**
     * 请求远端 compact/summarize session。
     */
    public Object compactSession(String sessionId, Map<String, Object> body, String traceId) {
        requireSessionUnlocked(sessionId);
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        Map<String, Object> request = safeBody(body);
        String providerId = text(request.get("providerID"));
        String modelId = text(request.get("modelID"));
        if ((providerId == null) != (modelId == null)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "压缩会话必须同时指定 providerID 和 modelID");
        }
        // V2 compact 请求不接收模型字段；先在固定 session 上选定前端指定的模型，避免悄悄改用旧模型。
        if (providerId != null) {
            LinkedHashMap<String, Object> model = new LinkedHashMap<>();
            model.put("providerID", providerId);
            model.put("id", modelId);
            String variant = text(request.get("variant"));
            if (variant != null) model.put("variant", variant);
            post(location, "/session/" + encodePath(location.remoteSessionId()) + "/model",
                    Map.of("model", model), traceId);
        }
        Object result = post(
                location,
                "/session/" + encodePath(location.remoteSessionId()) + "/summarize",
                request,
                traceId);
        if (sessionApplicationService != null) {
            sessionApplicationService.touchSession(new SessionId(sessionId), traceId);
        }
        return result;
    }

    /**
     * 请求远端 revert session。
     */
    public Object revertSession(String sessionId, Map<String, Object> body, String traceId) {
        requireSessionUnlocked(sessionId);
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/revert", safeBody(body), traceId);
    }

    /**
     * 请求远端 unrevert session。
     */
    public Object unrevertSession(String sessionId, Map<String, Object> body, String traceId) {
        requireSessionUnlocked(sessionId);
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return delete(location, "/session/" + encodePath(location.remoteSessionId()) + "/unrevert", Map.of(), traceId);
    }

    /**
     * 执行远端 session command。
     */
    public Object commandSession(String sessionId, Map<String, Object> body, String traceId) {
        requireNewMessageAllowed(traceId);
        requireSessionUnlocked(sessionId);
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/command", safeBody(body), traceId);
    }

    /**
     * 执行远端 session shell 命令；shell 安全边界由 API 层和 opencode runtime 共同约束。
     */
    public Object shellSession(String sessionId, Map<String, Object> body, String traceId) {
        requireNewMessageAllowed(traceId);
        requireSessionUnlocked(sessionId);
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/shell", safeBody(body), traceId);
    }

    /**
     * 创建 opencode 会话分享链接，sessionId 经平台映射后再访问远端。
     */
    public Object shareSession(String sessionId, String traceId) {
        throw new PlatformException(
                ErrorCode.API_GONE,
                "OpenCode V2 已移除原生分享接口，请使用平台 collaboration-share 接口");
    }

    /**
     * 取消 opencode 会话分享。
     */
    public Object unshareSession(String sessionId, String traceId) {
        throw new PlatformException(
                ErrorCode.API_GONE,
                "OpenCode V2 已移除原生分享接口，请使用平台 collaboration-share 接口");
    }

    private void requireSessionUnlocked(String sessionId) {
        if (sessionLockGuard != null) {
            sessionLockGuard.requireUnlocked(new SessionId(sessionId));
        }
    }

    /**
     * 查询远端 permission 请求列表。
     */
    public Object listPermissions(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return filterSessionInteractions(
                get(location, "/permission", Map.of(), traceId),
                location.remoteSessionId());
    }

    /**
     * 回复远端 permission 请求，并兼容前端 decision 字段到 opencode reply 字段。
     */
    public Object replyPermission(String sessionId, String requestId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        Object result;
        try {
            result = post(
                    location,
                    "/session/" + encodePath(location.remoteSessionId()) + "/permission/"
                            + encodePath(requestId) + "/reply",
                    permissionReplyBody(body),
                    traceId);
        } catch (PlatformException exception) {
            throw translateExpiredInteraction(exception, "permission", requestId, traceId);
        }
        reconcileAfterInteractionReply(sessionId, location, traceId);
        return result;
    }

    /**
     * 查询远端 question 请求列表。
     */
    public Object listQuestions(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        Object filtered = filterSessionInteractions(
                get(location, "/question", Map.of(), traceId),
                location.remoteSessionId());
        return projectQuestionForms(filtered);
    }

    /**
     * 回复远端 question 请求。
     */
    public Object replyQuestion(String sessionId, String requestId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        Object result;
        try {
            String formPath = "/session/" + encodePath(location.remoteSessionId())
                    + "/form/" + encodePath(requestId);
            Object detail = get(location, formPath, Map.of(), traceId);
            Map<?, ?> form = detail instanceof Map<?, ?> envelope && envelope.get("data") instanceof Map<?, ?> data
                    ? data : detail instanceof Map<?, ?> raw ? raw : Map.of();
            Map<String, Object> normalizedBody = OpencodeV2FormAdapter.toReply(safeBody(body), form);
            result = post(
                    location,
                    formPath + "/reply",
                    normalizedBody,
                    traceId);
        } catch (PlatformException exception) {
            throw translateExpiredInteraction(exception, "question", requestId, traceId);
        }
        recordQuestionReplyAcknowledged(sessionId, location, requestId, safeBody(body), traceId);
        reconcileAfterInteractionReply(sessionId, location, traceId);
        return result;
    }

    /**
     * 拒绝远端 question 请求。
     */
    public Object rejectQuestion(String sessionId, String requestId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        Object result;
        try {
            result = delete(
                    location,
                    "/session/" + encodePath(location.remoteSessionId()) + "/form/" + encodePath(requestId),
                    Map.of(),
                    traceId);
        } catch (PlatformException exception) {
            throw translateExpiredInteraction(exception, "question", requestId, traceId);
        }
        reconcileAfterInteractionReply(sessionId, location, traceId);
        return result;
    }

    /**
     * OpenCode 重启会清掉内存中的 pending ask；远端 404 只对交互请求转换为可恢复冲突，避免误报 502。
     */
    private PlatformException translateExpiredInteraction(
            PlatformException exception,
            String interactionType,
            String requestId,
            String traceId) {
        Object status = exception.details().get("status");
        if (!Integer.valueOf(404).equals(status)) {
            return exception;
        }
        return new PlatformException(
                ErrorCode.CONFLICT,
                "" + interactionType + "请求已失效，请重新发起对话",
                Map.of(
                        "interactionType", interactionType,
                        "requestId", requestId,
                        "reason", "REMOTE_INTERACTION_EXPIRED",
                        "traceId", traceId),
                exception);
    }

    private void reconcileAfterInteractionReply(
            String sessionId,
            AgentRuntimeTargetResolver.SessionRuntimeTarget location,
            String traceId) {
        if (runApplicationService == null) {
            return;
        }
        runApplicationService.reconcileAfterInteractionReply(
                new SessionId(sessionId),
                location.runtime().agentId(),
                traceId);
    }

    /**
     * 远端 HTTP 已接受 question 回复时立即记录平台事实，避免 OpenCode 既有事件订阅漏发
     * {@code question.replied} 后，运行态提醒和前端工具卡一直停留在待回答状态。
     */
    private void recordQuestionReplyAcknowledged(
            String sessionId,
            AgentRuntimeTargetResolver.SessionRuntimeTarget location,
            String requestId,
            Map<String, Object> normalizedBody,
            String traceId) {
        if (runApplicationService == null) {
            return;
        }
        runApplicationService.recordQuestionReplyAcknowledged(
                new SessionId(sessionId),
                location.remoteSessionId(),
                requestId,
                toQuestionAnswers(normalizedBody.get("answers")),
                traceId);
    }

    /**
     * 发起 MCP auth。
     */
    public Object startMcpAuth(String name, Map<String, Object> body, String traceId) {
        return post(workspaceLocation(text(safeBody(body).get("workspaceId")), traceId), "/mcp/" + encodePath(name) + "/auth", safeBody(body), traceId);
    }

    /**
     * 完成 MCP auth 回调。
     */
    public Object completeMcpAuth(String name, Map<String, Object> body, String traceId) {
        return post(
                workspaceLocation(text(safeBody(body).get("workspaceId")), traceId),
                "/mcp/" + encodePath(name) + "/auth/callback",
                safeBody(body),
                traceId);
    }

    /**
     * 执行 MCP auth authenticate 步骤。
     */
    public Object authenticateMcp(String name, Map<String, Object> body, String traceId) {
        return post(
                workspaceLocation(text(safeBody(body).get("workspaceId")), traceId),
                "/mcp/" + encodePath(name) + "/auth/authenticate",
                safeBody(body),
                traceId);
    }

    /**
     * 删除 MCP auth。
     */
    public Object removeMcpAuth(String name, String traceId) {
        return post(workspaceLocation(null, traceId), "/mcp/" + encodePath(name) + "/auth/disconnect", Map.of(), traceId);
    }

    /**
     * 发送 GET runtime 请求。
     */
    private Object get(AgentRuntimeTargetResolver.RuntimeTarget location, String path, Map<String, String> query, String traceId) {
        return call(location, "GET", path, query, null, traceId);
    }

    /**
     * 发送 POST runtime 请求。
     */
    private Object post(AgentRuntimeTargetResolver.RuntimeTarget location, String path, Map<String, Object> body, String traceId) {
        return call(location, "POST", path, Map.of(), body, traceId);
    }

    /**
     * 发送 PATCH runtime 请求。
     */
    private Object patch(AgentRuntimeTargetResolver.RuntimeTarget location, String path, Map<String, Object> body, String traceId) {
        return call(location, "PATCH", path, Map.of(), body, traceId);
    }

    /**
     * 发送 PUT runtime 请求。
     */
    private Object put(AgentRuntimeTargetResolver.RuntimeTarget location, String path, Map<String, Object> body, String traceId) {
        return call(location, "PUT", path, Map.of(), body, traceId);
    }

    /**
     * 发送 DELETE runtime 请求。
     */
    private Object delete(AgentRuntimeTargetResolver.RuntimeTarget location, String path, Map<String, Object> body, String traceId) {
        return call(location, "DELETE", path, Map.of(), body, traceId);
    }

    /**
     * 统一调用 AgentRuntime runtime 方法，并把 JsonNode projection 转回普通 Java 对象。
     */
    private Object call(AgentRuntimeTargetResolver.RuntimeTarget location, String method, String path, Map<String, String> query, Object body, String traceId) {
        try {
            AgentRuntimeResult result = location.runtime().runtime(new AgentRuntimeCommand(
                            location.node(),
                            method,
                            path,
                            location.directory(),
                            null,
                            query,
                            body,
                            traceId))
                    .block();
            Object response = objectMapper.convertValue(result.body(), Object.class);
            return experienceRuntimeTarget(location)
                    ? ExperienceWorkspacePathRedactor.redact(response, location.directory())
                    : response;
        } catch (PlatformException exception) {
            if (!experienceRuntimeTarget(location)) {
                throw exception;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> safeDetails = (Map<String, Object>) redactExperienceDirectory(
                    exception.details(), location.directory());
            // 不保留带物理路径的原异常为 cause，避免异常日志再次打印未脱敏消息。
            throw new PlatformException(
                    exception.errorCode(),
                    redactExperienceDirectory(exception.getMessage(), location.directory()).toString(),
                    safeDetails);
        }
    }

    private boolean experienceRuntimeTarget(AgentRuntimeTargetResolver.RuntimeTarget location) {
        return location != null
                && ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(location.workspaceId());
    }

    /** 兼容本服务既有调用点，统一委托共享递归脱敏器。 */
    private Object redactExperienceDirectory(Object value, String directory) {
        return ExperienceWorkspacePathRedactor.redact(value, directory);
    }

    /**
     * OpenCode 的 permission/question 列表是进程级列表，不按 session 路径过滤；平台接口必须按当前 binding
     * 的 remoteSessionId 再过滤一次，否则打开任意历史会话都会把其它会话的 ask 弹出来。
     */
    private Object filterSessionInteractions(Object response, String remoteSessionId) {
        if (remoteSessionId == null || remoteSessionId.isBlank()) {
            return List.of();
        }
        if (response instanceof List<?> list) {
            return list.stream()
                    .filter(item -> interactionBelongsToSession(item, remoteSessionId))
                    .toList();
        }
        if (!(response instanceof Map<?, ?> raw)) {
            return response;
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                envelope.put(stringKey, value);
            }
        });
        for (String key : List.of("data", "items", "permissions", "questions")) {
            Object value = envelope.get(key);
            if (value instanceof List<?> list) {
                envelope.put(key, list.stream()
                        .filter(item -> interactionBelongsToSession(item, remoteSessionId))
                        .toList());
                return envelope;
            }
        }
        return interactionBelongsToSession(envelope, remoteSessionId) ? envelope : List.of();
    }

    private boolean interactionBelongsToSession(Object item, String remoteSessionId) {
        if (!(item instanceof Map<?, ?> raw)) {
            return false;
        }
        for (String key : List.of("sessionID", "sessionId", "session_id")) {
            Object value = raw.get(key);
            if (value instanceof String session && !session.isBlank()) {
                return remoteSessionId.equals(session);
            }
        }
        for (String key : List.of("info", "request", "data")) {
            if (interactionBelongsToSession(raw.get(key), remoteSessionId)) {
                return true;
            }
        }
        return false;
    }

    /** V2 Form.Info 列表投影为现有 Question DTO，保留列表 envelope 和旧事件兼容。 */
    private Object projectQuestionForms(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::projectQuestionForm).toList();
        }
        if (value instanceof Map<?, ?> raw) {
            Object data = raw.get("data");
            if (data instanceof List<?> list) {
                LinkedHashMap<String, Object> envelope = new LinkedHashMap<>();
                raw.forEach((key, item) -> {
                    if (key instanceof String name) envelope.put(name, item);
                });
                envelope.put("data", list.stream().map(this::projectQuestionForm).toList());
                return envelope;
            }
            return projectQuestionForm(value);
        }
        return value;
    }

    private Object projectQuestionForm(Object value) {
        return value instanceof Map<?, ?> map && map.get("fields") instanceof List<?>
                ? OpencodeV2FormAdapter.toQuestion(map) : value;
    }

    private String extractSessionId(Object response) {
        if (!(response instanceof Map<?, ?> map)) {
            return null;
        }
        for (String key : List.of("id", "sessionID", "sessionId")) {
            String value = text(map.get(key));
            if (value != null) {
                return value;
            }
        }
        for (Object value : map.values()) {
            String nested = extractSessionId(value);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private ModelSelection parseModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        int separator = model.indexOf('/');
        if (separator <= 0 || separator >= model.length() - 1) {
            throw new IllegalArgumentException("model must use provider/model format");
        }
        return new ModelSelection(model.substring(0, separator), model.substring(separator + 1));
    }

    private record ModelSelection(String providerId, String modelId) {
    }

    /**
     * 构造 workspace 级 runtime target；未指定 workspace 时只选择可用节点，不传 directory。
     */
    private AgentRuntimeTargetResolver.WorkspaceRuntimeTarget workspaceLocation(String workspaceId, String traceId) {
        return targetResolver.workspaceTarget(currentAgentId(), currentUserId(), workspaceId, traceId);
    }

    /**
     * 体验工作区的文件能力只开放平台 WebSocket 通道；旧 runtime 文件接口会返回绝对路径，必须在转发前关闭。
     * 先解析目标以执行实时体验资格校验，再按稳定 ID 命名空间拒绝，管理员或 static token 均不能绕过。
     */
    private AgentRuntimeTargetResolver.WorkspaceRuntimeTarget runtimeFileLocation(String workspaceId, String traceId) {
        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location = workspaceLocation(workspaceId, traceId);
        WorkspaceId resolvedWorkspaceId = workspaceId == null || workspaceId.isBlank()
                ? null
                : new WorkspaceId(workspaceId);
        if (ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(resolvedWorkspaceId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "体验工作区文件只能通过平台文件通道访问");
        }
        return location;
    }

    /** 体验区不建立个人 worktree，列举和变更接口都在转发前关闭。 */
    private AgentRuntimeTargetResolver.WorkspaceRuntimeTarget nonExperienceWorkspaceLocation(
            String workspaceId,
            String traceId) {
        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location = workspaceLocation(workspaceId, traceId);
        WorkspaceId resolvedWorkspaceId = workspaceId == null || workspaceId.isBlank()
                ? null
                : new WorkspaceId(workspaceId);
        if (ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(resolvedWorkspaceId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "体验工作区不支持个人 worktree");
        }
        return location;
    }

    /**
     * 构造 session 级 runtime target，要求平台 Session 已绑定远端 agent session 和节点。
     */
    private AgentRuntimeTargetResolver.SessionRuntimeTarget sessionLocation(String sessionId, String traceId) {
        return targetResolver.sessionTarget(currentAgentId(), currentUserId(), sessionId, traceId);
    }

    /**
     * 返回当前请求上下文中的 agentId；未设置时兼容旧入口默认 opencode。
     */
    private String currentAgentId() {
        return agentRuntimeRegistry.normalize(agentContext.get());
    }

    /**
     * 返回当前请求上下文中的用户 ID；为空时代表 static-token 或兼容调用。
     */
    private UserId currentUserId() {
        return userContext.get();
    }

    /** legacy runtime 中仍会触发新推理的入口统一复用公共配置与用户级 dispose 门禁。 */
    private void requireNewMessageAllowed(String traceId) {
        UserId userId = currentUserId();
        if (AgentRuntimeRegistry.DEFAULT_AGENT_ID.equals(currentAgentId())) {
            publicConfigMessageGate.requireAllowed(userId);
        }
        if (userRuntimeDisposeCoordinator != null) {
            userRuntimeDisposeCoordinator.requireNotDisposing(userId, traceId);
        }
    }

    private void setUserContext(UserId userId) {
        if (userId == null) {
            userContext.remove();
        } else {
            userContext.set(userId);
        }
    }

    private void restoreAgentContext(String previous) {
        if (previous == null) {
            agentContext.remove();
        } else {
            agentContext.set(previous);
        }
    }

    private void restoreUserContext(UserId previousUser) {
        if (previousUser == null) {
            userContext.remove();
        } else {
            userContext.set(previousUser);
        }
    }

    /**
     * 构造单值 query，空白值按缺失处理。
     */
    private Map<String, String> query(String name, String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return Map.of(name, value);
    }

    /**
     * 规范化可选请求体，null body 以空对象发送。
     */
    private Map<String, Object> safeBody(Map<String, Object> body) {
        return body == null ? Map.of() : body;
    }

    /** worktree mutation 必须绑定受权平台 Workspace，平台路由字段不会透传给 OpenCode。 */
    private String requiredWorktreeWorkspaceId(Map<String, Object> body) {
        String workspaceId = text(safeBody(body).get("workspaceId"));
        if (workspaceId == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "worktree 操作必须指定 workspaceId");
        }
        return workspaceId;
    }

    private Map<String, Object> worktreeBody(Map<String, Object> body) {
        Map<String, Object> forwarded = new LinkedHashMap<>(safeBody(body));
        forwarded.remove("workspaceId");
        return forwarded;
    }

    private String requiredWorktreeDirectory(Map<String, Object> body) {
        String directory = text(body.get("directory"));
        if (directory == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "worktree 操作必须指定已登记目录");
        }
        return directory;
    }

    /**
     * remove/reset 只接受目标项目实时 worktree 列表返回的精确目录，禁止把任意物理路径交给
     * OpenCode 的递归删除兼容分支。
     */
    private void requireListedWorktree(
            AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location,
            String projectId,
            String directory,
            String traceId) {
        Object listed = get(location, "/experimental/worktree", Map.of("projectID", projectId), traceId);
        boolean registered = listed instanceof List<?> items && items.stream().anyMatch(item ->
                item instanceof Map<?, ?> worktree && directory.equals(text(worktree.get("directory"))));
        if (!registered) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "worktree 目录未登记或已失效");
        }
    }

    /** 工作区目录对应的 V2 projectID 只能从当前进程的 location 查询，不能用平台 Workspace ID 代替。 */
    private String opencodeProjectId(AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location, String traceId) {
        Object info = get(location, "/api/location", Map.of(), traceId);
        if (info instanceof Map<?, ?> raw && raw.get("project") instanceof Map<?, ?> project) {
            String projectId = text(project.get("id"));
            if (projectId != null) return projectId;
        }
        throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "OpenCode V2 location 未返回 projectID");
    }

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /** 从 V2 tool part 的 input/metadata/state 兼容位置提取最新 Todo 快照。 */
    private List<Map<String, Object>> todoSnapshotFromPart(Map<String, Object> part) {
        if (!"tool".equals(part.get("type"))) {
            return null;
        }
        String toolName = text(part.get("toolName"));
        if (toolName == null) {
            toolName = text(part.get("tool"));
        }
        if (toolName == null || !"todowrite".equalsIgnoreCase(toolName)) {
            return null;
        }
        List<Map<String, Object>> snapshot = todoItems(part.get("input"));
        if (snapshot != null) {
            return snapshot;
        }
        snapshot = todoItems(part.get("metadata"));
        if (snapshot != null) {
            return snapshot;
        }
        Object state = part.get("state");
        if (state instanceof Map<?, ?> stateMap) {
            snapshot = todoItems(stateMap.get("input"));
            if (snapshot != null) {
                return snapshot;
            }
            return todoItems(stateMap.get("metadata"));
        }
        return null;
    }

    /** 兼容 V2 Todo 的数组、todos、todo 和 items 包装。 */
    private List<Map<String, Object>> todoItems(Object value) {
        Object raw = value;
        if (value instanceof Map<?, ?> map) {
            raw = map.containsKey("todos") ? map.get("todos")
                    : map.containsKey("todo") ? map.get("todo") : map.get("items");
        }
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> {
                    LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
                    ((Map<?, ?>) item).forEach((key, itemValue) -> {
                        if (key instanceof String name && itemValue != null) {
                            normalized.put(name, itemValue);
                        }
                    });
                    return (Map<String, Object>) normalized;
                })
                .toList();
    }

    /**
     * 兼容前端 permission decision/reply 字段，统一转换为 V2 的 decision envelope。
     */
    private Map<String, Object> permissionReplyBody(Map<String, Object> body) {
        Map<String, Object> source = safeBody(body);
        Object reply = source.getOrDefault("reply", source.get("decision"));
        if (reply == null) {
            return source;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("decision", reply);
        if (source.containsKey("message")) {
            normalized.put("message", source.get("message"));
        }
        return normalized;
    }

    /**
     * 把前端 answers 归一化为平台事件审计使用的 {@code List<List<String>>}。
     * <ul>
     *   <li>null 或非数组 → 空列表；</li>
     *   <li>空数组 → 空列表；</li>
     *   <li>已是嵌套（首元素为 List）→ 逐个内层数组转 String 透传；</li>
     *   <li>扁平标量数组 → 整体包成单个内层数组。</li>
     * </ul>
     */
    private List<List<String>> toQuestionAnswers(Object answers) {
        if (!(answers instanceof List<?> outer) || outer.isEmpty()) {
            return List.of();
        }
        // 已嵌套：每个元素本身就是某问题的答案数组，逐项转 String 透传。
        if (outer.get(0) instanceof List<?>) {
            return outer.stream()
                    .filter(element -> element instanceof List<?>)
                    .map(element -> toStringList((List<?>) element))
                    .toList();
        }
        // 扁平：整组 label 属于同一个问题，包成单个内层数组。
        return List.of(toStringList(outer));
    }

    /**
     * 把任意 List 的元素转为 String 列表，null 元素跳过。
     */
    private List<String> toStringList(List<?> list) {
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    /**
     * 对路径片段逐段 URL 编码，保留分段斜杠并避免空段进入远端路径。
     */
    private String encodePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] segments = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(encodePathSegment(segment));
        }
        return builder.toString();
    }

    /** 对单个远端 URL path segment 编码，供同包的受控 runtime 调用复用。 */
    static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

}
