package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.agent.runtime.AgentCancelCommand;
import com.icbc.testagent.agent.runtime.AgentPromptPart;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeCommand;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentRuntimeResult;
import com.icbc.testagent.agent.runtime.AgentStartRunCommand;
import com.icbc.testagent.agent.runtime.AgentStreamEventsCommand;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.routing.ExecutionNodeRouter;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingDecisionRepository;
import com.icbc.testagent.domain.routing.RoutingReason;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.event.RunEventAppender;
import com.icbc.testagent.event.RunEventLiveBus;
import com.icbc.testagent.opencode.runtime.model.ModelCatalogApplicationService;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.runtime.runtime.AgentRuntimeTargetResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Run 应用服务，集中编排持久化、路由、agent 启动/取消和 RunEvent 追加。
 */
@Service
public class RunApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunApplicationService.class);
    private static final int ROUTING_CANDIDATE_LIMIT = 50;
    private static final String DEFAULT_OPENCODE_AGENT = "build";
    private static final Set<String> LIVE_DIFF_TOOLS = Set.of("write", "edit", "apply_patch");

    private final WorkspaceRepository workspaceRepository;
    private final com.icbc.testagent.domain.session.SessionRepository sessionRepository;
    private final RunRepository runRepository;
    private final SessionMessageRepository sessionMessageRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final RoutingDecisionRepository routingDecisionRepository;
    private final RunEventAppender runEventAppender;
    private final AgentRuntimeRegistry agentRuntimeRegistry;
    private final AgentSessionBindingRepository agentSessionBindingRepository;
    private final RunEventLiveBus runEventLiveBus;
    private final RunEventPersistencePolicy runEventPersistencePolicy;
    private final ModelCatalogApplicationService modelCatalogService;
    private final UserOpencodeProcessAssignmentService userProcessAssignmentService;
    private final AgentRuntimeTargetResolver runtimeTargetResolver;
    private final ExecutionNodeRouter executionNodeRouter = new ExecutionNodeRouter();

    /**
     * 创建生产用 Run 编排服务，显式注入实时事件总线和持久化策略。
     */
    @Autowired
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.icbc.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.routingDecisionRepository = Objects.requireNonNull(routingDecisionRepository, "routingDecisionRepository must not be null");
        this.runEventAppender = Objects.requireNonNull(runEventAppender, "runEventAppender must not be null");
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.agentSessionBindingRepository = Objects.requireNonNull(agentSessionBindingRepository, "agentSessionBindingRepository must not be null");
        this.runEventLiveBus = Objects.requireNonNull(runEventLiveBus, "runEventLiveBus must not be null");
        this.runEventPersistencePolicy = Objects.requireNonNull(runEventPersistencePolicy, "runEventPersistencePolicy must not be null");
        this.modelCatalogService = modelCatalogService;
        this.userProcessAssignmentService = userProcessAssignmentService;
        this.runtimeTargetResolver = new AgentRuntimeTargetResolver(
                workspaceRepository,
                sessionRepository,
                executionNodeRepository,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                userProcessAssignmentService);
    }

    /**
     * 兼容旧单测的构造器，保留显式实时事件总线和持久化策略注入。
     */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.icbc.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runEventLiveBus,
                runEventPersistencePolicy,
                null,
                null);
    }

    /**
     * 创建兼容旧测试的服务实例，使用默认实时事件总线和默认事件持久化策略。
     */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.icbc.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            UserOpencodeProcessAssignmentService userProcessAssignmentService) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                userProcessAssignmentService);
    }

    /**
     * 创建兼容旧测试的服务实例，使用默认实时事件总线和默认事件持久化策略。
     */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.icbc.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                null);
    }

    /**
     * 以纯文本 prompt 启动 Run，兼容早期只传字符串的调用方。
     */
    public Run startRun(SessionId sessionId, String prompt, String traceId) {
        return startRun(agentRuntimeRegistry.defaultAgentId(), StartRunInput.ofPrompt(sessionId, prompt), traceId);
    }

    /**
     * 按指定 agent 启动纯文本 Run，agentId 来源于 URL path。
     */
    public Run startRun(String agentId, SessionId sessionId, String prompt, String traceId) {
        return startRun(agentId, StartRunInput.ofPrompt(sessionId, prompt), traceId);
    }

    /**
     * 启动一次平台 Run：创建本地 Run/消息、路由 agent 节点、启动远端 prompt 并订阅事件流。
     */
    public Run startRun(StartRunInput input, String traceId) {
        return startRun(agentRuntimeRegistry.defaultAgentId(), input, traceId);
    }

    /**
     * 启动一次指定 agent 的平台 Run，所有 agent 复用同一 RunEvent 和错误处理链路。
     */
    public Run startRun(String agentId, StartRunInput input, String traceId) {
        return startRunInternal(null, agentId, input, traceId);
    }

    /**
     * 以当前登录用户启动默认 opencode Run；HTTP 入口使用该方法强制执行用户进程防护。
     */
    public Run startRun(UserId userId, StartRunInput input, String traceId) {
        return startRun(userId, agentRuntimeRegistry.defaultAgentId(), input, traceId);
    }

    /**
     * 以当前登录用户启动指定 agent Run；opencode 会先解析用户专属进程。
     */
    public Run startRun(UserId userId, String agentId, StartRunInput input, String traceId) {
        return startRunInternal(Objects.requireNonNull(userId, "userId must not be null"), agentId, input, traceId);
    }

    private Run startRunInternal(UserId userId, String agentId, StartRunInput input, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        UserOpencodeProcessAssignment userProcessAssignment = resolveUserProcessAssignment(userId, resolvedAgentId, traceId);
        Instant now = Instant.now();
        SessionId sessionId = input.sessionId();
        String prompt = input.effectivePrompt();
        Session session = findSession(sessionId);
        Workspace workspace = findWorkspace(session.workspaceId());
        Run pending = new Run(
                new RunId(RuntimeIdGenerator.runId()),
                session.sessionId(),
                workspace.workspaceId(),
                RunStatus.PENDING,
                now,
                now,
                traceId);
        runRepository.save(pending);
        saveUserMessage(session.sessionId(), prompt, traceId, now);
        append(pending.runId(), RunEventType.RUN_CREATED, traceId, now, Map.of("status", RunStatus.PENDING.name()));

        try {
            AgentRoutingTarget target = userProcessAssignment == null
                    ? resolveAgentTarget(resolvedAgentId, session, pending.runId(), now, traceId)
                    : userProcessTarget(userProcessAssignment, pending.runId(), now, traceId);
            routingDecisionRepository.save(target.decision());
            AgentSessionBinding binding = runtimeTargetResolver.ensureAgentSession(
                    resolvedAgentId,
                    runtime,
                    session,
                    workspace,
                    target.node(),
                    traceId);
            ModelSelection model = parseModel(input.model());
            String opencodeAgent = resolveOpencodeAgent(input);
            syncProviderConfig(runtime, target.node(), traceId);
            Run running = runRepository.save(pending.start(Instant.now()));
            append(running.runId(), RunEventType.RUN_STARTED, traceId, Instant.now(), Map.of("status", RunStatus.RUNNING.name()));
            // 先订阅事件再触发 prompt，避免 opencode 快速失败或快速返回时平台漏掉终态事件。
            subscribeAgentEvents(runtime, running, target.node(), workspace, traceId);
            runtime.startRun(new AgentStartRunCommand(
                            target.node(),
                            binding.remoteSessionId(),
                            workspace.rootPath(),
                            null,
                            prompt,
                            toAgentPromptParts(input, workspace),
                            input.messageId(),
                            opencodeAgent,
                            model.providerId(),
                            model.modelId(),
                            input.variant(),
                            traceId))
                    .block();
            return running;
        } catch (PlatformException exception) {
            Run failed = runRepository.save(pending.fail(Instant.now()));
            append(failed.runId(), RunEventType.RUN_FAILED, traceId, Instant.now(), Map.of("errorCode", exception.errorCode().name()));
            throw exception;
        }
    }

    private UserOpencodeProcessAssignment resolveUserProcessAssignment(UserId userId, String agentId, String traceId) {
        return runtimeTargetResolver.resolveUserProcessAssignment(userId, agentId, traceId).orElse(null);
    }

    private AgentRoutingTarget userProcessTarget(
            UserOpencodeProcessAssignment assignment,
            RunId runId,
            Instant now,
            String traceId) {
        return new AgentRoutingTarget(
                assignment.node(),
                new RoutingDecision(runId, assignment.node().executionNodeId(), RoutingReason.MANUAL_OVERRIDE, now, traceId));
    }

    /**
     * opencode prompt_async 会把 agent 写入 assistant message；不传时会触发 SQLite 非空约束失败。
     */
    private String resolveOpencodeAgent(StartRunInput input) {
        return Optional.ofNullable(input.agent())
                .or(() -> Optional.ofNullable(input.mode()))
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_OPENCODE_AGENT);
    }

    /**
     * 将平台 prompt parts 转成 opencode prompt_async parts，缺少显式文本时保留 legacy prompt。
     */
    private List<AgentPromptPart> toAgentPromptParts(StartRunInput input, Workspace workspace) {
        if (input.parts().isEmpty()) {
            return List.of(AgentPromptPart.text(input.effectivePrompt()));
        }
        List<AgentPromptPart> parts = new ArrayList<>();
        boolean hasTextPart = input.parts().stream()
                .anyMatch(part -> "text".equals(part.type()) && part.text() != null && !part.text().isBlank());
        if (input.prompt() != null && !hasTextPart) {
            parts.add(AgentPromptPart.text(input.prompt()));
        }
        parts.addAll(input.parts().stream()
                .map(part -> toAgentPromptPart(part, workspace))
                .filter(Objects::nonNull)
                .toList());
        return parts.isEmpty() ? List.of(AgentPromptPart.text(input.effectivePrompt())) : parts;
    }

    /**
     * 托管模型源启用时，在真正 prompt_async 前尽力同步 provider 定义到 opencode。
     */
    private void syncProviderConfig(AgentRuntime runtime, ExecutionNode node, String traceId) {
        if (modelCatalogService != null && modelCatalogService.managedSourceEnabled()) {
            modelCatalogService.syncProviderConfig(runtime, node, traceId);
        }
    }

    /**
     * 按 part 类型分发到 opencode text/file/agent 表达，未知类型静默丢弃。
     */
    private AgentPromptPart toAgentPromptPart(StartRunInput.PromptPart part, Workspace workspace) {
        if (part.type() == null) {
            return null;
        }
        return switch (part.type()) {
            case "text" -> part.text() == null ? null : AgentPromptPart.text(part.text());
            case "file" -> toAgentFilePart(part, workspace);
            case "agent" -> toAgentAgentPart(part);
            case "reference" -> toReferenceTextPart(part);
            default -> null;
        };
    }

    /**
     * 将平台文件上下文转成 opencode file part，优先使用内联文本，其次使用前端给出的 URL，再兜底 workspace file URL。
     */
    private AgentPromptPart toAgentFilePart(StartRunInput.PromptPart part, Workspace workspace) {
        String mime = firstText(part.mimeType(), "text/plain");
        String filename = firstText(part.name(), filenameFromPath(part.path()), "attachment");
        String text = sourceText(part);
        if (text != null) {
            String url = "data:" + mime + ";base64," + Base64.getEncoder()
                    .encodeToString(text.getBytes(StandardCharsets.UTF_8));
            return AgentPromptPart.file(url, mime, filename, fileSource(part, text));
        }
        if (part.url() != null) {
            return AgentPromptPart.file(part.url(), mime, filename, normalizedSource(part.source()));
        }
        if (part.path() != null) {
            // 只允许把 workspace 内路径转成 file:// URL，防止前端构造任意宿主机路径交给 opencode 读取。
            return AgentPromptPart.file(workspaceFileUrl(workspace, part.path()), mime, filename, fileSource(part, null));
        }
        return null;
    }

    /**
     * 将平台 agent part 转成 opencode agent part，缺少名称时丢弃。
     */
    private AgentPromptPart toAgentAgentPart(StartRunInput.PromptPart part) {
        String agentName = firstText(part.name(), part.agentId());
        if (agentName == null) {
            return null;
        }
        return AgentPromptPart.agent(agentName, agentSource(part));
    }

    /**
     * 将 reference part 降级为文本提示，避免 opencode 不认识平台引用类型。
     */
    private AgentPromptPart toReferenceTextPart(StartRunInput.PromptPart part) {
        String label = firstText(part.label(), part.id(), part.uri());
        if (label == null) {
            return null;
        }
        String suffix = part.uri() == null ? "" : " (" + part.uri() + ")";
        return AgentPromptPart.text("Reference: " + label + suffix);
    }

    /**
     * 构造文件 source 元数据，保留路径和选区文本范围供 opencode Web 投影使用。
     */
    private Map<String, Object> fileSource(StartRunInput.PromptPart part, String text) {
        String path = firstText(part.path(), part.name());
        if (path == null && text == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("type", "file");
        if (path != null) {
            source.put("path", path);
        }
        if (text != null) {
            source.put("text", Map.of(
                    "value", text,
                    "start", sourceNumber(part.source(), "start", 0),
                    "end", sourceNumber(part.source(), "end", text.length())));
        }
        return Map.copyOf(source);
    }

    /**
     * 构造 agent source 元数据，保留前端输入范围用于 opencode 侧上下文显示。
     */
    private Map<String, Object> agentSource(StartRunInput.PromptPart part) {
        String value = sourceText(part);
        if (value == null) {
            return Map.of();
        }
        return Map.of(
                "value", value,
                "start", sourceNumber(part.source(), "start", 0),
                "end", sourceNumber(part.source(), "end", value.length()));
    }

    /**
     * 固化 source Map，null source 按空上下文处理。
     */
    private Map<String, Object> normalizedSource(Map<String, Object> source) {
        return source == null ? Map.of() : Map.copyOf(source);
    }

    /**
     * 将 workspace 相对路径转成 file URL，并拒绝路径穿越到 workspace 根目录外。
     */
    private String workspaceFileUrl(Workspace workspace, String path) {
        Path root = Path.of(workspace.rootPath()).toAbsolutePath().normalize();
        Path target = root.resolve(path).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "文件上下文必须位于当前 Workspace 内",
                    Map.of("path", path, "workspaceId", workspace.workspaceId().value()));
        }
        return target.toUri().toString();
    }

    /**
     * 从 prompt part 的 content 或 source.text 中提取非空文本内容。
     */
    private String sourceText(StartRunInput.PromptPart part) {
        if (part.content() != null) {
            return part.content();
        }
        Object text = part.source().get("text");
        if (text instanceof String value && !value.isBlank()) {
            return value;
        }
        if (text instanceof Map<?, ?> nested) {
            Object value = nested.get("value");
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    /**
     * 从 source Map 中读取整数字段，缺失或非数字时使用 fallback。
     */
    private int sourceNumber(Map<String, Object> source, String key, int fallback) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    /**
     * 从路径中提取文件名，路径异常或没有文件名时保留原始 path。
     */
    private String filenameFromPath(String path) {
        if (path == null) {
            return null;
        }
        Path filename = Path.of(path).getFileName();
        return filename == null ? path : filename.toString();
    }

    /**
     * 解析 provider/model 形式的模型选择，格式不合法时不向 opencode 指定模型。
     */
    private ModelSelection parseModel(String model) {
        if (model == null) {
            return new ModelSelection(null, null);
        }
        int slash = model.indexOf('/');
        if (slash <= 0 || slash == model.length() - 1) {
            return new ModelSelection(null, null);
        }
        return new ModelSelection(model.substring(0, slash), model.substring(slash + 1));
    }

    /**
     * 返回第一个非空白字符串，用于可选字段兜底。
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 查询本地 Run，不存在时抛出统一 NOT_FOUND 平台异常。
     */
    public Run getRun(RunId runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Run 不存在", Map.of("runId", runId.value())));
    }

    /**
     * 请求取消 Run：先更新本地状态，再尽力通知默认 agent 远端 session abort，最后落取消事件。
     */
    public Run cancelRun(RunId runId, String traceId) {
        return cancelRun(agentRuntimeRegistry.defaultAgentId(), runId, traceId);
    }

    /**
     * 请求取消指定 agent Run；旧 URL 默认传入 opencode，新 URL 由 path 决定。
     */
    public Run cancelRun(String agentId, RunId runId, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        Run run = getRun(runId);
        if (run.status().isTerminal()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Run 已结束，不能取消",
                    Map.of("runId", runId.value(), "status", run.status().name()));
        }
        Run cancelling = run.status() == RunStatus.CANCELLING
                ? run
                : runRepository.save(run.requestCancel(Instant.now()));
        if (cancelling.status() == RunStatus.CANCELLING) {
            append(runId, RunEventType.RUN_CANCELLING, traceId, Instant.now(), Map.of("status", cancelling.status().name()));
        }
        RoutingDecision decision = routingDecisionRepository.findByRunId(runId).orElse(null);
        if (decision != null) {
            Session session = findSession(run.sessionId());
            Workspace workspace = findWorkspace(run.workspaceId());
            runtimeTargetResolver.findAgentBinding(resolvedAgentId, session, traceId).ifPresent(binding ->
                    executionNodeRepository.findById(binding.executionNodeId()).ifPresent(node ->
                            runtime.cancelSession(new AgentCancelCommand(
                                        node,
                                        binding.remoteSessionId(),
                                        workspace.rootPath(),
                                        null,
                                        traceId))
                                    .block()));
        }
        Run cancelled = cancelling.status() == RunStatus.CANCELLED
                ? cancelling
                : runRepository.save(cancelling.cancel(Instant.now()));
        append(runId, RunEventType.RUN_CANCELLED, traceId, Instant.now(), Map.of("status", cancelled.status().name()));
        return cancelled;
    }

    /**
     * 保存用户消息投影，只保存用户输入文本，不保存远端 assistant 内容。
     */
    private void saveUserMessage(SessionId sessionId, String prompt, String traceId, Instant createdAt) {
        sessionMessageRepository.save(new SessionMessage(
                new SessionMessageId(RuntimeIdGenerator.messageId()),
                sessionId,
                SessionMessageRole.USER,
                prompt,
                createdAt,
                traceId));
    }

    /**
     * 查询 Session，不存在时转换为统一 NOT_FOUND 异常。
     */
    private Session findSession(SessionId sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在", Map.of("sessionId", sessionId.value())));
    }

    /**
     * 查询 Workspace，不存在时转换为统一 NOT_FOUND 异常。
     */
    private Workspace findWorkspace(com.icbc.testagent.domain.workspace.WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
    }

    /**
     * 解析本次 Run 的 agent 目标节点；已有远端 session 时强制粘滞到绑定节点。
     */
    private AgentRoutingTarget resolveAgentTarget(String agentId, Session session, RunId runId, Instant now, String traceId) {
        Optional<AgentSessionBinding> binding = runtimeTargetResolver.findAgentBinding(agentId, session, traceId);
        if (binding.isPresent()) {
            ExecutionNode node = executionNodeRepository.findById(binding.get().executionNodeId())
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.OPENCODE_UNAVAILABLE,
                            "会话绑定的 agent 执行节点不存在",
                            Map.of(
                                    "sessionId", session.sessionId().value(),
                                    "agentId", agentId,
                                    "nodeId", binding.get().executionNodeId().value())));
            if (!node.canAcceptRun()) {
                throw new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "会话绑定的 agent 执行节点不可用",
                        Map.of(
                                "sessionId", session.sessionId().value(),
                                "agentId", agentId,
                                "nodeId", node.executionNodeId().value(),
                                "status", node.status().name()));
            }
            return new AgentRoutingTarget(
                    node,
                    new RoutingDecision(runId, node.executionNodeId(), RoutingReason.STICKY_SESSION, now, traceId));
        }

        RoutingDecision decision = executionNodeRouter.route(
                runId,
                executionNodeRepository.findRoutableNodes(ROUTING_CANDIDATE_LIMIT),
                now,
                traceId);
        ExecutionNode node = executionNodeRepository.findById(decision.executionNodeId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "路由节点不存在",
                        Map.of("nodeId", decision.executionNodeId().value())));
        return new AgentRoutingTarget(node, decision);
    }

    /**
     * 追加平台 RunEvent，统一封装 RunEventDraft 构造。
     */
    private void append(RunId runId, RunEventType type, String traceId, Instant occurredAt, Map<String, Object> payload) {
        runEventAppender.append(new RunEventDraft(runId, type, traceId, occurredAt, payload));
    }

    /**
     * 订阅 agent 事件流，事件处理串行 offload，避免阻塞 Netty 线程。
     */
    private void subscribeAgentEvents(AgentRuntime runtime, Run run, ExecutionNode node, Workspace workspace, String traceId) {
        runtime.streamRunEvents(new AgentStreamEventsCommand(
                        node,
                        run.runId(),
                        workspace.rootPath(),
                        null,
                        traceId))
                // opencode stream 来自 Netty 线程，事件入库或实时发布必须串行 offload，且本地 DB 抖动不能误判为 Run 失败。
                .concatMap(draft -> Mono.fromRunnable(() -> appendStreamEvent(runtime, run, node, workspace, draft))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(error -> {
                            LOGGER.warn(
                                    "Failed to handle opencode stream event, runId={}, eventType={}, traceId={}",
                                    run.runId().value(),
                                    draft.type().wireName(),
                                    traceId,
                                    error);
                            return Mono.empty();
                        }))
                .doOnError(error -> failRunFromStream(run, traceId, error))
                .subscribe(ignored -> {
                }, ignored -> {
                    // 错误已在 doOnError 中落库，这里消费异常以避免 Reactor dropped error 日志。
                });
    }

    /**
     * 处理单个 agent 事件：终态事件落库并更新 Run，瞬态消息事件只发布 live bus。
     */
    private void appendStreamEvent(AgentRuntime runtime, Run originalRun, ExecutionNode node, Workspace workspace, RunEventDraft draft) {
        if (draft.type() == RunEventType.RUN_SUCCEEDED || draft.type() == RunEventType.RUN_FAILED) {
            Run current = runRepository.findById(originalRun.runId()).orElse(originalRun);
            if (!current.status().isTerminal()) {
                if (draft.type() == RunEventType.RUN_SUCCEEDED) {
                    runRepository.save(current.succeed(draft.occurredAt()));
                } else {
                    runRepository.save(current.fail(draft.occurredAt()));
                }
                runEventAppender.append(runEventPersistencePolicy.sanitizeForPersistence(draft));
            }
            return;
        }
        if (draft.type() == RunEventType.MESSAGE_PART_UPDATED) {
            appendLiveDiffFromToolPart(runtime, originalRun, node, workspace, draft);
        }
        if (!runEventPersistencePolicy.shouldPersist(draft)) {
            runEventLiveBus.publishTransient(runEventPersistencePolicy.sanitizeForPersistence(draft));
            return;
        }
        runEventAppender.append(runEventPersistencePolicy.sanitizeForPersistence(draft));
    }

    /**
     * 从 agent tool part 完成态派生轻量 Diff 事件，供前端在 Run 未结束时实时刷新文件树。
     */
    private void appendLiveDiffFromToolPart(AgentRuntime runtime, Run originalRun, ExecutionNode node, Workspace workspace, RunEventDraft draft) {
        try {
            liveDiffFromToolPart(runtime, originalRun, node, workspace, draft)
                    .ifPresent(diff -> runEventAppender.append(runEventPersistencePolicy.sanitizeForPersistence(diff)));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to derive live diff from tool part, runId={}, traceId={}",
                    originalRun.runId().value(),
                    draft.traceId(),
                    exception);
        }
    }

    private Optional<RunEventDraft> liveDiffFromToolPart(
            AgentRuntime runtime,
            Run originalRun,
            ExecutionNode node,
            Workspace workspace,
            RunEventDraft draft) {
        Map<String, Object> rawPart = mapValue(draft.payload().get("part")).orElse(draft.payload());
        if (!"tool".equals(textValue(rawPart.get("type")).orElse(null))) {
            return Optional.empty();
        }
        Map<String, Object> state = mapValue(rawPart.get("state")).orElse(Map.of());
        String status = firstMapText(rawPart, "status").or(() -> firstMapText(state, "status")).orElse("");
        if (!"completed".equals(status)) {
            return Optional.empty();
        }
        String tool = firstMapText(rawPart, "toolName", "tool", "name").orElse("");
        if (!LIVE_DIFF_TOOLS.contains(tool)) {
            return Optional.empty();
        }
        Map<String, Object> input = mapValue(state.get("input"))
                .or(() -> mapValue(rawPart.get("input")))
                .orElse(Map.of());
        Map<String, Object> metadata = mapValue(state.get("metadata"))
                .or(() -> mapValue(rawPart.get("metadata")))
                .orElse(Map.of());
        List<LiveDiffFile> files = extractToolDiffFiles(tool, input, metadata, workspace);
        boolean needsWorkingTreeDiff = "write".equals(tool) || files.isEmpty() || files.stream().anyMatch(file -> !file.countsKnown());
        if (needsWorkingTreeDiff) {
            List<LiveDiffFile> workingTreeFiles = workingTreeDiffFiles(runtime, node, workspace, draft.traceId());
            if (!workingTreeFiles.isEmpty()) {
                files = workingTreeFiles;
            } else if ("write".equals(tool) || files.isEmpty()) {
                return Optional.empty();
            }
        }
        return Optional.of(new RunEventDraft(
                originalRun.runId(),
                RunEventType.DIFF_PROPOSED,
                draft.traceId(),
                draft.occurredAt(),
                liveDiffPayload(tool, rawPart, draft.payload(), files)));
    }

    private Map<String, Object> liveDiffPayload(
            String tool,
            Map<String, Object> rawPart,
            Map<String, Object> rawPayload,
            List<LiveDiffFile> files) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "tool");
        payload.put("tool", tool);
        firstMapText(rawPayload, "messageID", "messageId")
                .or(() -> firstMapText(rawPart, "messageID", "messageId"))
                .ifPresent(messageId -> {
                    payload.put("messageID", messageId);
                    payload.put("messageId", messageId);
                });
        firstMapText(rawPayload, "partID", "partId")
                .or(() -> firstMapText(rawPart, "partID", "partId", "id"))
                .ifPresent(partId -> {
                    payload.put("partID", partId);
                    payload.put("partId", partId);
                });
        payload.put("files", files.stream().map(LiveDiffFile::toPayload).toList());
        return Map.copyOf(payload);
    }

    private List<LiveDiffFile> extractToolDiffFiles(
            String tool,
            Map<String, Object> input,
            Map<String, Object> metadata,
            Workspace workspace) {
        if ("edit".equals(tool)) {
            return mapValue(metadata.get("filediff"))
                    .flatMap(file -> diffFileFromMap(file, workspace, "modified"))
                    .map(List::of)
                    .orElse(List.of());
        }
        if ("apply_patch".equals(tool)) {
            Object filesValue = metadata.get("files");
            if (!(filesValue instanceof List<?> items)) {
                return List.of();
            }
            return items.stream()
                    .map(this::mapValue)
                    .flatMap(Optional::stream)
                    .map(file -> diffFileFromMap(file, workspace, statusFromPatchType(firstMapText(file, "type", "status").orElse(null))))
                    .flatMap(Optional::stream)
                    .toList();
        }
        String path = firstMapText(metadata, "filepath", "filePath", "path", "file")
                .or(() -> firstMapText(input, "filePath", "path", "file"))
                .flatMap(value -> normalizeWorkspacePath(workspace, value))
                .orElse(null);
        return path == null ? List.of() : List.of(new LiveDiffFile(path, "", 0, 0, "modified", false));
    }

    private Optional<LiveDiffFile> diffFileFromMap(Map<String, Object> value, Workspace workspace, String fallbackStatus) {
        Optional<String> path = firstMapText(value, "path", "file", "filePath", "relativePath")
                .flatMap(raw -> normalizeWorkspacePath(workspace, raw));
        if (path.isEmpty()) {
            return Optional.empty();
        }
        Optional<Integer> additions = numberValue(value.get("additions"));
        Optional<Integer> deletions = numberValue(value.get("deletions"));
        return Optional.of(new LiveDiffFile(
                path.get(),
                firstMapText(value, "patch", "diff").orElse(""),
                additions.orElse(0),
                deletions.orElse(0),
                firstMapText(value, "status").orElse(fallbackStatus),
                additions.isPresent() && deletions.isPresent()));
    }

    private List<LiveDiffFile> workingTreeDiffFiles(AgentRuntime runtime, ExecutionNode node, Workspace workspace, String traceId) {
        AgentRuntimeResult result = runtime.runtime(new AgentRuntimeCommand(
                        node,
                        "GET",
                        "/vcs/diff",
                        workspace.rootPath(),
                        null,
                        Map.of("mode", "working"),
                        null,
                        traceId))
                .block();
        if (result == null) {
            return List.of();
        }
        JsonNode filesNode = diffFilesNode(result.body());
        if (filesNode == null || !filesNode.isArray()) {
            return List.of();
        }
        List<LiveDiffFile> files = new ArrayList<>();
        filesNode.forEach(item -> diffFileFromJson(item, workspace).ifPresent(files::add));
        return files;
    }

    private JsonNode diffFilesNode(JsonNode body) {
        if (body == null || body.isNull()) {
            return null;
        }
        if (body.isArray()) {
            return body;
        }
        if (body.has("data") && body.get("data").isArray()) {
            return body.get("data");
        }
        if (body.has("files") && body.get("files").isArray()) {
            return body.get("files");
        }
        if (body.has("items") && body.get("items").isArray()) {
            return body.get("items");
        }
        return null;
    }

    private Optional<LiveDiffFile> diffFileFromJson(JsonNode item, Workspace workspace) {
        if (item == null || !item.isObject()) {
            return Optional.empty();
        }
        Optional<String> path = firstJsonText(item, "path", "file", "filePath", "relativePath")
                .flatMap(raw -> normalizeWorkspacePath(workspace, raw));
        if (path.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LiveDiffFile(
                path.get(),
                firstJsonText(item, "patch", "diff").orElse(""),
                jsonInt(item, "additions").orElse(0),
                jsonInt(item, "deletions").orElse(0),
                firstJsonText(item, "status").orElse("modified"),
                item.has("additions") && item.has("deletions")));
    }

    private String statusFromPatchType(String type) {
        if (type == null) {
            return "modified";
        }
        return switch (type) {
            case "add", "added", "create", "created" -> "added";
            case "delete", "deleted", "remove", "removed" -> "deleted";
            default -> "modified";
        };
    }

    private Optional<String> normalizeWorkspacePath(Workspace workspace, String rawPath) {
        if (rawPath == null || rawPath.isBlank() || "/dev/null".equals(rawPath)) {
            return Optional.empty();
        }
        String path = rawPath.replaceFirst("^([ab])/", "");
        String root = workspace.rootPath();
        if (path.equals(root) || path.startsWith(root + "/")) {
            path = path.substring(root.length()).replaceFirst("^/+", "");
        }
        path = path.replaceFirst("^\\./", "");
        return path.isBlank() || path.startsWith("/") ? Optional.empty() : Optional.of(path);
    }

    private Optional<Map<String, Object>> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Optional.empty();
        }
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key instanceof String stringKey) {
                map.put(stringKey, item);
            }
        });
        return Optional.of(map);
    }

    private Optional<String> firstMapText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Optional<String> value = textValue(map.get(key));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> textValue(Object value) {
        return value instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }

    private Optional<Integer> numberValue(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstJsonText(JsonNode item, String... keys) {
        for (String key : keys) {
            JsonNode value = item.get(key);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return Optional.of(value.asText());
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> jsonInt(JsonNode item, String key) {
        JsonNode value = item.get(key);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (value.isInt() || value.isLong()) {
            return Optional.of(value.asInt());
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            try {
                return Optional.of(Integer.parseInt(value.asText()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * 事件流异常时尽力把 Run 标记失败；失败落库本身异常只记录日志。
     */
    private void failRunFromStream(Run run, String traceId, Throwable error) {
        try {
            Run current = runRepository.findById(run.runId()).orElse(run);
            if (!current.status().isTerminal()) {
                Run failed = runRepository.save(current.fail(Instant.now()));
                append(failed.runId(), RunEventType.RUN_FAILED, traceId, Instant.now(),
                        Map.of("error", error.getClass().getSimpleName()));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to persist opencode stream failure, runId={}, traceId={}",
                    run.runId().value(), traceId, exception);
        }
    }

    private record AgentRoutingTarget(ExecutionNode node, RoutingDecision decision) {
    }

    private record LiveDiffFile(
            String path,
            String patch,
            int additions,
            int deletions,
            String status,
            boolean countsKnown) {

        private Map<String, Object> toPayload() {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("path", path);
            payload.put("patch", patch);
            payload.put("additions", additions);
            payload.put("deletions", deletions);
            payload.put("status", status);
            return Map.copyOf(payload);
        }
    }

    private record ModelSelection(String providerId, String modelId) {
    }
}
