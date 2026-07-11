package com.icbc.testagent.opencode.runtime.runtime;

import com.icbc.testagent.agent.runtime.AgentRuntimeCommand;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentRuntimeResult;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesResult;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.opencode.runtime.model.ModelCatalogApplicationService;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.runtime.run.RunApplicationService;
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
        return get(workspaceLocation(workspaceId, traceId), "/agent", Map.of(), traceId);
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
        return get(workspaceLocation(workspaceId, traceId), "/global/health", Map.of(), traceId);
    }

    /**
     * 读取 opencode 文件列表，path 缺省时使用当前目录。
     */
    public Object fsList(String workspaceId, String path, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/file", query("path", path == null || path.isBlank() ? "." : path), traceId);
    }

    /**
     * 调用 opencode 文件搜索 API。
     */
    public Object fsFind(String workspaceId, String query, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/find/file", query("query", query), traceId);
    }

    /**
     * 读取 opencode workspace 文件内容。
     */
    public Object fsRead(String workspaceId, String path, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/file/content", query("path", path), traceId);
    }

    /**
     * 读取远端 VCS 状态。
     */
    public Object vcsStatus(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/vcs/status", Map.of(), traceId);
    }

    /**
     * 读取远端 VCS Diff，mode 缺省为 working，context 仅在调用方传入时透传。
     */
    public Object vcsDiff(String workspaceId, String mode, Integer context, String traceId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("mode", mode == null || mode.isBlank() ? "working" : mode);
        if (context != null) {
            query.put("context", Integer.toString(context));
        }
        return get(workspaceLocation(workspaceId, traceId), "/vcs/diff", query, traceId);
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
     * 读取 opencode 全局配置；仍按 workspace 路由节点，避免前端直连 opencode server。
     */
    public Object getConfig(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/global/config", Map.of(), traceId);
    }

    /**
     * 更新 opencode 全局配置，body 只做空值兜底，字段兼容由 opencode runtime 负责。
     */
    public Object updateConfig(String workspaceId, Map<String, Object> body, String traceId) {
        return patch(workspaceLocation(workspaceId, traceId), "/global/config", safeBody(body), traceId);
    }

    /**
     * 触发 opencode runtime dispose，用于 Web App 设置页的服务重载能力。
     */
    public Object disposeGlobal(String traceId) {
        return post(workspaceLocation(null, traceId), "/global/dispose", Map.of(), traceId);
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
        return post(workspaceLocation(null, traceId), "/provider/" + encodePath(providerId) + "/oauth/authorize", safeBody(body), traceId);
    }

    /**
     * 完成 provider OAuth 回调。
     */
    public Object completeProviderOAuth(String providerId, Map<String, Object> body, String traceId) {
        return post(workspaceLocation(null, traceId), "/provider/" + encodePath(providerId) + "/oauth/callback", safeBody(body), traceId);
    }

    /**
     * 写入 provider auth secret，secret 不在应用层记录日志或持久化。
     */
    public Object setProviderAuth(String providerId, Map<String, Object> body, String traceId) {
        return put(workspaceLocation(null, traceId), "/auth/" + encodePath(providerId), safeBody(body), traceId);
    }

    /**
     * 删除 provider auth secret。
     */
    public Object removeProviderAuth(String providerId, String traceId) {
        return delete(workspaceLocation(null, traceId), "/auth/" + encodePath(providerId), Map.of(), traceId);
    }

    /**
     * 查询 opencode experimental worktree 列表。
     */
    public Object listWorktrees(String workspaceId, String traceId) {
        return get(workspaceLocation(workspaceId, traceId), "/experimental/worktree", Map.of(), traceId);
    }

    /**
     * 创建 worktree；workspaceId 只用于平台路由，不透传为额外策略。
     */
    public Object createWorktree(Map<String, Object> body, String traceId) {
        return post(workspaceLocation(text(safeBody(body).get("workspaceId")), traceId), "/experimental/worktree", safeBody(body), traceId);
    }

    /**
     * 删除 worktree。
     */
    public Object removeWorktree(Map<String, Object> body, String traceId) {
        return delete(workspaceLocation(text(safeBody(body).get("workspaceId")), traceId), "/experimental/worktree", safeBody(body), traceId);
    }

    /**
     * 重置 worktree。
     */
    public Object resetWorktree(Map<String, Object> body, String traceId) {
        return post(workspaceLocation(text(safeBody(body).get("workspaceId")), traceId), "/experimental/worktree/reset", safeBody(body), traceId);
    }

    /**
     * 查询远端 session children，平台 sessionId 会先映射为 opencode session id。
     */
    public Object sessionChildren(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return get(location, "/session/" + encodePath(location.remoteSessionId()) + "/children", Map.of(), traceId);
    }

    /**
     * 查询远端 session todo。
     */
    public Object sessionTodo(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return get(location, "/session/" + encodePath(location.remoteSessionId()) + "/todo", Map.of(), traceId);
    }

    /**
     * 查询远端 session Diff，messageId 为空时不发送 messageID query。
     */
    public Object sessionDiff(String sessionId, String messageId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return get(location, "/session/" + encodePath(location.remoteSessionId()) + "/diff", query("messageID", messageId), traceId);
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
            if (shouldCompact) {
                if (model == null) {
                    throw new IllegalArgumentException("side question requires model provider/model when context compaction is needed");
                }
                post(
                        location,
                        "/session/" + encodePath(temporarySessionId) + "/summarize",
                        Map.of("providerID", model.providerId(), "modelID", model.modelId()),
                        traceId);
                compacted = true;
            }

            LinkedHashMap<String, Object> messageBody = new LinkedHashMap<>();
            if (input.agent() != null) {
                messageBody.put("agent", input.agent());
            }
            if (model != null) {
                messageBody.put("model", Map.of("providerID", model.providerId(), "modelID", model.modelId()));
            }
            // 使用 plan agent 的权限边界允许只读检查；不再用 tools=false，否则模型只能把工具调用协议写成文本而无法得到工具结果。
            messageBody.put("system", SideQuestionPolicy.SYSTEM_PROMPT);
            messageBody.put("parts", List.of(Map.of("type", "text", "text", question)));
            Object answerResponse = post(
                    location,
                    "/session/" + encodePath(temporarySessionId) + "/message",
                    messageBody,
                    traceId);
            String answer = SIDE_QUESTION_ANSWER_EXTRACTOR.extract(answerResponse);
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

    /**
     * 请求远端 compact/summarize session。
     */
    public Object compactSession(String sessionId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/summarize", safeBody(body), traceId);
    }

    /**
     * 请求远端 revert session。
     */
    public Object revertSession(String sessionId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/revert", safeBody(body), traceId);
    }

    /**
     * 请求远端 unrevert session。
     */
    public Object unrevertSession(String sessionId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/unrevert", safeBody(body), traceId);
    }

    /**
     * 执行远端 session command。
     */
    public Object commandSession(String sessionId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/command", safeBody(body), traceId);
    }

    /**
     * 执行远端 session shell 命令；shell 安全边界由 API 层和 opencode runtime 共同约束。
     */
    public Object shellSession(String sessionId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/shell", safeBody(body), traceId);
    }

    /**
     * 创建 opencode 会话分享链接，sessionId 经平台映射后再访问远端。
     */
    public Object shareSession(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return post(location, "/session/" + encodePath(location.remoteSessionId()) + "/share", Map.of(), traceId);
    }

    /**
     * 取消 opencode 会话分享。
     */
    public Object unshareSession(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return delete(location, "/session/" + encodePath(location.remoteSessionId()) + "/share", Map.of(), traceId);
    }

    /**
     * 查询远端 permission 请求列表。
     */
    public Object listPermissions(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return get(location, "/permission", Map.of(), traceId);
    }

    /**
     * 回复远端 permission 请求，并兼容前端 decision 字段到 opencode reply 字段。
     */
    public Object replyPermission(String sessionId, String requestId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        Object result = post(
                location,
                "/permission/" + encodePath(requestId) + "/reply",
                permissionReplyBody(body),
                traceId);
        reconcileAfterInteractionReply(sessionId, location, traceId);
        return result;
    }

    /**
     * 查询远端 question 请求列表。
     */
    public Object listQuestions(String sessionId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        return get(location, "/question", Map.of(), traceId);
    }

    /**
     * 回复远端 question 请求。
     */
    public Object replyQuestion(String sessionId, String requestId, Map<String, Object> body, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        Object result = post(
                location,
                "/question/" + encodePath(requestId) + "/reply",
                questionReplyBody(body),
                traceId);
        reconcileAfterInteractionReply(sessionId, location, traceId);
        return result;
    }

    /**
     * 拒绝远端 question 请求。
     */
    public Object rejectQuestion(String sessionId, String requestId, String traceId) {
        AgentRuntimeTargetResolver.SessionRuntimeTarget location = sessionLocation(sessionId, traceId);
        Object result = post(
                location,
                "/question/" + encodePath(requestId) + "/reject",
                Map.of(),
                traceId);
        reconcileAfterInteractionReply(sessionId, location, traceId);
        return result;
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
        return delete(workspaceLocation(null, traceId), "/mcp/" + encodePath(name) + "/auth", Map.of(), traceId);
    }

    /**
     * 发送 GET runtime 请求。
     */
    private Object get(AgentRuntimeTargetResolver.RuntimeTarget location, String path, Map<String, String> query, String traceId) {
        return call(location, "GET", path, query, null, traceId);
    }

    /**
     * 当前模型列表由平台托管时，先把 provider 配置尽力写入 opencode runtime。
     */
    private void syncProviderConfig(String workspaceId, String traceId) {
        AgentRuntimeTargetResolver.WorkspaceRuntimeTarget location = workspaceLocation(workspaceId, traceId);
        modelCatalogService.syncProviderConfig(location.runtime(), location.node(), traceId);
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
        return objectMapper.convertValue(result.body(), Object.class);
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

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /**
     * 兼容前端 permission decision 字段，转换为 opencode 期望的 reply 字段。
     */
    private Map<String, Object> permissionReplyBody(Map<String, Object> body) {
        Map<String, Object> source = safeBody(body);
        Object reply = source.getOrDefault("reply", source.get("decision"));
        if (reply == null) {
            return source;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("reply", reply);
        if (source.containsKey("message")) {
            normalized.put("message", source.get("message"));
        }
        return normalized;
    }

    /**
     * 兼容前端扁平 answers 字段，转换为 opencode 期望的嵌套结构。
     * <p>
     * opencode {@code /question/{requestId}/reply} 要求 {@code answers} 为 {@code List<List<String>>}：
     * 外层数组每个问题一个内层数组，内层放选中的 label。前端 {@code RuntimeDock} 只发送扁平
     * {@code string[]}（单选 {@code [label]}、文本 {@code [text]}、多选 {@code [l1,l2]}），
     * 且每条回复只针对一个问题，因此把扁平数组整体包成单个内层数组即可。
     * 对已嵌套或空数组做幂等处理，避免重复包装。
     */
    private Map<String, Object> questionReplyBody(Map<String, Object> body) {
        Map<String, Object> source = safeBody(body);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("answers", toQuestionAnswers(source.get("answers")));
        return normalized;
    }

    /**
     * 把前端 answers 归一化为 opencode 的 {@code List<List<String>>}。
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
