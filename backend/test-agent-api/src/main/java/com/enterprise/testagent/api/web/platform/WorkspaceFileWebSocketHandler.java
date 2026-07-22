package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.observability.TraceIdSupport;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import com.enterprise.testagent.workspace.WorkspaceDirectoryService;
import com.enterprise.testagent.workspace.WorkspaceFileUpload;
import com.enterprise.testagent.workspace.WorkspaceViewApplicationService;
import com.enterprise.testagent.workspace.WorkspaceViewLocator;
import com.enterprise.testagent.workspace.WorkspaceViewLocatorKind;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * 工作空间文件 RPC WebSocket handler，使用一次性 ticket 建立连接，消息内只接受白名单文件操作。
 */
@Component
public class WorkspaceFileWebSocketHandler implements WebSocketHandler {

    private static final String MODE_DIRECTORY_PICKER = "directory-picker";
    private static final String MODE_WORKSPACE = "workspace";
    private static final String MODE_AGENT_CONFIG = "agent-config";
    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_WORKSPACE = "WORKSPACE";
    private static final int MAX_ACTIVE_UPLOADS = 4;

    private final WorkspaceFileSocketTicketService ticketService;
    private final WorkspaceApplicationService workspaceService;
    private final WorkspaceDirectoryService directoryService;
    private final AgentConfigApplicationService agentConfigService;
    private final WorkspaceViewApplicationService workspaceViewService;
    private final ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;

    /**
     * 装配文件 WebSocket handler 依赖和 Origin 白名单。
     */
    @Autowired
    WorkspaceFileWebSocketHandler(
            WorkspaceFileSocketTicketService ticketService,
            WorkspaceApplicationService workspaceService,
            WorkspaceDirectoryService directoryService,
            AgentConfigApplicationService agentConfigService,
            WorkspaceViewApplicationService workspaceViewService,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer,
            ObjectMapper objectMapper,
            @Value("${test-agent.security.cors-allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:4177,http://127.0.0.1:4177,http://localhost:4187,http://127.0.0.1:4187,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
            String allowedOrigins) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService must not be null");
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
        this.directoryService = Objects.requireNonNull(directoryService, "directoryService must not be null");
        this.agentConfigService = Objects.requireNonNull(agentConfigService, "agentConfigService must not be null");
        this.workspaceViewService = Objects.requireNonNull(workspaceViewService, "workspaceViewService must not be null");
        this.workspaceAccessAuthorizer = Objects.requireNonNull(
                workspaceAccessAuthorizer,
                "workspaceAccessAuthorizer must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.allowedOrigins = Set.copyOf(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
    }

    /** 兼容既有 handler 单元测试构造路径；生产装配始终使用带组合视图和实时鉴权的构造器。 */
    public WorkspaceFileWebSocketHandler(
            WorkspaceFileSocketTicketService ticketService,
            WorkspaceApplicationService workspaceService,
            WorkspaceDirectoryService directoryService,
            AgentConfigApplicationService agentConfigService,
            ObjectMapper objectMapper,
            String allowedOrigins) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService must not be null");
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
        this.directoryService = Objects.requireNonNull(directoryService, "directoryService must not be null");
        this.agentConfigService = Objects.requireNonNull(agentConfigService, "agentConfigService must not be null");
        this.workspaceViewService = null;
        this.workspaceAccessAuthorizer = (userId, workspaceId) -> { };
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.allowedOrigins = Set.copyOf(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
    }

    /**
     * 处理文件 RPC WebSocket 生命周期：校验 Origin、消费 ticket、执行请求并发送统一响应。
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String traceId = traceId(session.getHandshakeInfo().getHeaders());
        WorkspaceFileSocketTicket ticket;
        try {
            String origin = session.getHandshakeInfo().getHeaders().getOrigin();
            if (!allowedOrigins.contains(origin)) {
                return sendErrorAndClose(session, null, "FORBIDDEN", "origin denied", traceId, Map.of());
            }
            ticket = ticketService.consume(query(session.getHandshakeInfo().getUri(), "ticket"), origin);
        } catch (PlatformException exception) {
            return sendErrorAndClose(session, null, exception.errorCode().name(), exception.getMessage(), traceId, exception.details());
        } catch (Exception exception) {
            return sendErrorAndClose(session, null, "FORBIDDEN", "文件 WebSocket 拒绝连接", traceId, Map.of());
        }
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        WorkspaceFileSocketTicket activeTicket = ticket;
        // 请求本身由 concatMap 串行执行；连接取消可能从另一线程触发清理，因此会话表仍使用并发容器。
        Map<String, ActiveUpload> activeUploads = new ConcurrentHashMap<>();
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(payload -> Mono.fromCallable(() -> handleMessage(activeTicket, payload, traceId, activeUploads))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(outbound::tryEmitNext)
                        .then())
                .doFinally(ignored -> {
                    abortUploads(activeUploads);
                    outbound.tryEmitComplete();
                })
                .then();
        Mono<Void> sender = session.send(outbound.asFlux().map(session::textMessage));
        return Mono.when(inbound, sender);
    }

    private String handleMessage(
            WorkspaceFileSocketTicket ticket,
            String payload,
            String traceId,
            Map<String, ActiveUpload> activeUploads) {
        String id = null;
        try {
            JsonNode root = objectMapper.readTree(payload);
            id = text(root, "id");
            String op = requiredText(root, "op");
            JsonNode params = root.path("params");
            if (MODE_WORKSPACE.equals(ticket.mode()) && op.startsWith("workspace.")) {
                authorizeWorkspaceRpc(ticket, params);
            }
            Object data = switch (op) {
                case "workspace.list" -> workspaceService.listFiles(workspaceId(ticket, params), text(params, "path"));
                case "workspace.search" -> workspaceService.searchFiles(workspaceId(ticket, params), text(params, "query"));
                case "workspace.read" -> workspaceService.readFile(workspaceId(ticket, params), requiredText(params, "path"));
                case "workspace.read.chunk" -> workspaceService.readFilePreviewChunk(
                        workspaceId(ticket, params),
                        requiredText(params, "path"),
                        requiredNonNegativeLong(params, "offset"),
                        optionalNonNegativeLong(params, "expectedSize"),
                        optionalNonNegativeLong(params, "expectedLastModifiedMillis"));
                case "workspace.write" -> {
                    WorkspaceId workspaceId = workspaceId(ticket, params);
                    String path = requiredText(params, "path");
                    requireWorkspaceWrite(ticket, workspaceId, path);
                    workspaceService.writeFile(workspaceId, path, text(params, "content"));
                    yield null;
                }
                case "workspace.upload" -> {
                    WorkspaceId workspaceId = workspaceId(ticket, params);
                    String path = requiredText(params, "path");
                    requireWorkspaceWrite(ticket, workspaceId, path);
                    workspaceService.uploadFile(workspaceId, path, text(params, "contentBase64"));
                    yield null;
                }
                case "workspace.upload.begin" -> workspaceUploadBegin(ticket, params, activeUploads);
                case "workspace.upload.chunk" -> workspaceUploadChunk(ticket, params, activeUploads);
                case "workspace.upload.complete" -> workspaceUploadComplete(ticket, params, activeUploads);
                case "workspace.upload.abort" -> {
                    workspaceUploadAbort(ticket, params, activeUploads);
                    yield null;
                }
                case "workspace.copy" -> {
                    WorkspaceId workspaceId = workspaceId(ticket, params);
                    String sourcePath = requiredText(params, "sourcePath");
                    String targetPath = requiredText(params, "targetPath");
                    requireWorkspaceWrite(ticket, workspaceId, sourcePath);
                    requireWorkspaceWrite(ticket, workspaceId, targetPath);
                    workspaceService.copyFile(workspaceId, sourcePath, targetPath);
                    yield null;
                }
                case "workspace.move" -> {
                    WorkspaceId workspaceId = workspaceId(ticket, params);
                    String sourcePath = requiredText(params, "sourcePath");
                    String targetPath = requiredText(params, "targetPath");
                    requireWorkspaceWrite(ticket, workspaceId, sourcePath);
                    requireWorkspaceWrite(ticket, workspaceId, targetPath);
                    workspaceService.moveFile(workspaceId, sourcePath, targetPath);
                    yield null;
                }
                case "workspace.rename" -> {
                    WorkspaceId workspaceId = workspaceId(ticket, params);
                    String path = requiredText(params, "path");
                    requireWorkspaceWrite(ticket, workspaceId, path);
                    workspaceService.renameFile(
                            workspaceId,
                            path,
                            requiredText(params, "name"));
                    yield null;
                }
                case "workspace.status" -> workspaceService.fileStatus(workspaceId(ticket, params), requiredText(params, "path"));
                case "workspace.delete" -> {
                    WorkspaceId workspaceId = workspaceId(ticket, params);
                    String path = requiredText(params, "path");
                    requireWorkspaceWrite(ticket, workspaceId, path);
                    workspaceService.deleteFile(workspaceId, path);
                    yield null;
                }
                case "workspace.mkdir" -> {
                    WorkspaceId workspaceId = workspaceId(ticket, params);
                    String path = requiredText(params, "path");
                    requireWorkspaceWrite(ticket, workspaceId, path);
                    workspaceService.createDirectory(workspaceId, path);
                    yield null;
                }
                case "workspace.view.list" -> workspaceViewService.list(
                        workspaceId(ticket, params),
                        viewLocator(params));
                case "workspace.view.read" -> workspaceViewService.read(
                        workspaceId(ticket, params),
                        viewLocator(params));
                case "workspace.view.read.chunk" -> workspaceViewService.readChunk(
                        workspaceId(ticket, params),
                        viewLocator(params),
                        requiredNonNegativeLong(params, "offset"),
                        optionalNonNegativeLong(params, "expectedSize"),
                        optionalNonNegativeLong(params, "expectedLastModifiedMillis"));
                case "agent-config.list" -> agentConfigList(ticket, params);
                case "agent-config.read" -> agentConfigRead(ticket, params);
                case "agent-config.read.chunk" -> agentConfigReadChunk(ticket, params);
                case "agent-config.write" -> {
                    agentConfigWrite(ticket, params);
                    yield null;
                }
                case "agent-config.upload" -> {
                    agentConfigUpload(ticket, params);
                    yield null;
                }
                case "agent-config.upload.begin" -> agentConfigUploadBegin(ticket, params, activeUploads);
                case "agent-config.upload.chunk" -> agentConfigUploadChunk(ticket, params, activeUploads);
                case "agent-config.upload.complete" -> agentConfigUploadComplete(ticket, params, activeUploads);
                case "agent-config.upload.abort" -> {
                    agentConfigUploadAbort(ticket, params, activeUploads);
                    yield null;
                }
                case "agent-config.rename" -> {
                    agentConfigRename(ticket, params);
                    yield null;
                }
                case "agent-config.delete" -> {
                    agentConfigDelete(ticket, params);
                    yield null;
                }
                case "directory.list" -> directoryList(ticket, params);
                case "workspace.create" -> createWorkspace(ticket, params, traceId);
                default -> throw new PlatformException(ErrorCode.VALIDATION_ERROR, "不支持的文件 WebSocket 操作", Map.of("op", op));
            };
            return success(id, data, traceId);
        } catch (PlatformException exception) {
            return error(id, exception.errorCode().name(), exception.getMessage(), traceId, exception.details());
        } catch (Exception exception) {
            return error(id, ErrorCode.VALIDATION_ERROR.name(), "文件 WebSocket 消息无效", traceId, Map.of());
        }
    }

    private Object workspaceUploadBegin(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            Map<String, ActiveUpload> activeUploads) {
        requireUploadCapacity(activeUploads);
        WorkspaceId workspaceId = workspaceId(ticket, params);
        String path = requiredText(params, "path");
        requireWorkspaceWrite(ticket, workspaceId, path);
        WorkspaceFileUpload upload = workspaceService.beginFileUpload(
                workspaceId,
                path,
                requiredNonNegativeLong(params, "size"));
        return registerUpload(
                activeUploads,
                new ActiveUpload(UploadKind.WORKSPACE, upload, path, workspaceId.value(), null, null));
    }

    private Object workspaceUploadChunk(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            Map<String, ActiveUpload> activeUploads) {
        String uploadId = requiredText(params, "uploadId");
        ActiveUpload active = requireUpload(activeUploads, uploadId, UploadKind.WORKSPACE);
        WorkspaceId workspaceId = workspaceId(ticket, params);
        requireUploadContext(active, workspaceId.value(), null, null);
        requireWorkspaceWrite(ticket, workspaceId, active.path());
        try {
            active.upload().append(requiredNonNegativeLong(params, "index"), text(params, "contentBase64"));
            return Map.of(
                    "uploadedBytes", active.upload().uploadedBytes(),
                    "totalBytes", active.upload().expectedBytes());
        } catch (RuntimeException exception) {
            failUpload(activeUploads, uploadId, active);
            throw exception;
        }
    }

    private Object workspaceUploadComplete(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            Map<String, ActiveUpload> activeUploads) {
        String uploadId = requiredText(params, "uploadId");
        ActiveUpload active = requireUpload(activeUploads, uploadId, UploadKind.WORKSPACE);
        WorkspaceId workspaceId = workspaceId(ticket, params);
        requireUploadContext(active, workspaceId.value(), null, null);
        requireWorkspaceWrite(ticket, workspaceId, active.path());
        activeUploads.remove(uploadId);
        try {
            return Map.of("size", active.upload().complete());
        } catch (RuntimeException exception) {
            active.upload().abort();
            throw exception;
        }
    }

    private void workspaceUploadAbort(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            Map<String, ActiveUpload> activeUploads) {
        String uploadId = requiredText(params, "uploadId");
        ActiveUpload active = requireUpload(activeUploads, uploadId, UploadKind.WORKSPACE);
        WorkspaceId workspaceId = workspaceId(ticket, params);
        requireUploadContext(active, workspaceId.value(), null, null);
        requireWorkspaceWrite(ticket, workspaceId, active.path());
        activeUploads.remove(uploadId);
        active.upload().abort();
    }

    private void authorizeWorkspaceRpc(WorkspaceFileSocketTicket ticket, JsonNode params) {
        WorkspaceId workspaceId = workspaceId(ticket, params);
        if (ticket.userId() == null || ticket.userId().isBlank()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "工作区文件 ticket 缺少用户身份");
        }
        workspaceAccessAuthorizer.requireFileAccess(
                new com.enterprise.testagent.domain.user.UserId(ticket.userId()),
                workspaceId,
                ticket.superAdmin());
    }

    private WorkspaceViewLocator viewLocator(JsonNode params) {
        JsonNode locator = params == null ? null : params.get("locator");
        if (locator == null || !locator.isObject()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "workspace view locator 无效");
        }
        if (locator.has("physicalPath") || locator.has("rootPath") || locator.has("repositoryId")) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "workspace view locator 禁止携带物理路径或 repositoryId");
        }
        String kindValue = requiredText(locator, "kind");
        WorkspaceViewLocatorKind kind;
        try {
            kind = WorkspaceViewLocatorKind.valueOf(kindValue.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "workspace view locator kind 无效");
        }
        return new WorkspaceViewLocator(kind, text(locator, "path"), text(locator, "referenceAlias"));
    }

    private void requireWorkspaceWrite(WorkspaceFileSocketTicket ticket, WorkspaceId workspaceId, String path) {
        if (ticket.userId() != null) {
            workspaceService.requireWorkspaceWriteAccess(
                    workspaceId,
                    new com.enterprise.testagent.domain.user.UserId(ticket.userId()),
                    ticket.appAdmin());
        }
        if (protectedConfigPath(path) && !ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Agent、Skill、Tools 和 Templates 仅应用管理员可编辑");
        }
    }

    private com.enterprise.testagent.domain.user.UserId ticketUserId(WorkspaceFileSocketTicket ticket) {
        return ticket.userId() == null
                ? null
                : new com.enterprise.testagent.domain.user.UserId(ticket.userId());
    }

    private boolean protectedConfigPath(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        try {
            // 权限判断必须先折叠 ./ 与 ../，避免等价路径绕过受保护配置目录校验。
            normalized = java.nio.file.Path.of(normalized).normalize().toString().replace('\\', '/');
        } catch (RuntimeException exception) {
            return true;
        }
        return normalized.equals(".opencode")
                || normalized.equals(".opencode/agents")
                || normalized.startsWith(".opencode/agents/")
                || normalized.equals(".opencode/skills")
                || normalized.startsWith(".opencode/skills/")
                || normalized.equals(".opencode/tools")
                || normalized.startsWith(".opencode/tools/");
    }

    private Object directoryList(WorkspaceFileSocketTicket ticket, JsonNode params) {
        if (!MODE_DIRECTORY_PICKER.equals(ticket.mode())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前 ticket 不允许浏览服务器目录");
        }
        return directoryService.listServerDirectories(text(params, "path"), workspaceService.defaultDirectory());
    }

    private Object createWorkspace(WorkspaceFileSocketTicket ticket, JsonNode params, String traceId) {
        if (!ticket.superAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
        }
        if (!ticket.linuxServerId().equals(ticket.agentLinuxServerId())) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("workspaceLinuxServerId", ticket.linuxServerId());
            details.put("agentLinuxServerId", ticket.agentLinuxServerId());
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "工作空间与 agent 不在同一服务器",
                    details);
        }
        Workspace workspace = workspaceService.createWorkspace(
                requiredText(params, "name"),
                requiredText(params, "rootPath"),
                ticket.linuxServerId(),
                traceId);
        return RuntimeDtos.WorkspaceResponse.from(workspace);
    }

    private Object agentConfigList(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        String worktreeId = agentConfigWorktreeId(ticket, params);
        if (SCOPE_PUBLIC.equals(scope)) {
            return agentConfigService.listPublicAgentFiles(text(params, "path"), worktreeId, ticketUserId(ticket));
        }
        return agentConfigService.listWorkspaceAgentFiles(agentConfigWorkspaceId(ticket, params), text(params, "path"), worktreeId);
    }

    private Object agentConfigRead(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        String worktreeId = agentConfigWorktreeId(ticket, params);
        if (SCOPE_PUBLIC.equals(scope)) {
            return agentConfigService.readPublicAgentFile(requiredText(params, "path"), worktreeId, ticketUserId(ticket));
        }
        return agentConfigService.readWorkspaceAgentFile(agentConfigWorkspaceId(ticket, params), requiredText(params, "path"), worktreeId);
    }

    private Object agentConfigReadChunk(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        String worktreeId = agentConfigWorktreeId(ticket, params);
        String path = requiredText(params, "path");
        long offset = requiredNonNegativeLong(params, "offset");
        Long expectedSize = optionalNonNegativeLong(params, "expectedSize");
        Long expectedLastModifiedMillis = optionalNonNegativeLong(params, "expectedLastModifiedMillis");
        if (SCOPE_PUBLIC.equals(scope)) {
            return agentConfigService.readPublicAgentFilePreviewChunk(
                    path,
                    offset,
                    expectedSize,
                    expectedLastModifiedMillis,
                    worktreeId,
                    ticketUserId(ticket));
        }
        return agentConfigService.readWorkspaceAgentFilePreviewChunk(
                agentConfigWorkspaceId(ticket, params),
                path,
                offset,
                expectedSize,
                expectedLastModifiedMillis,
                worktreeId);
    }

    private void agentConfigWrite(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        if (SCOPE_PUBLIC.equals(scope)) {
            if (!ticket.superAdmin()) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
            }
            String worktreeId = agentConfigWorktreeId(ticket, params);
            agentConfigService.writePublicAgentFile(
                    requiredText(params, "path"),
                    text(params, "content"),
                    worktreeId,
                    ticketUserId(ticket));
            return;
        }
        if (!ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用 Agent 配置仅应用管理员可编辑");
        }
        String worktreeId = agentConfigWorktreeId(ticket, params);
        agentConfigService.writeWorkspaceAgentFile(agentConfigWorkspaceId(ticket, params), requiredText(params, "path"), text(params, "content"), worktreeId);
    }

    private void agentConfigUpload(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        String path = requiredText(params, "path");
        String contentBase64 = text(params, "contentBase64");
        if (SCOPE_PUBLIC.equals(scope)) {
            if (!ticket.superAdmin()) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
            }
            agentConfigService.uploadPublicAgentFile(
                    path,
                    contentBase64,
                    agentConfigWorktreeId(ticket, params),
                    ticketUserId(ticket));
            return;
        }
        if (!ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用 Agent 配置仅应用管理员可编辑");
        }
        agentConfigService.uploadWorkspaceAgentFile(
                agentConfigWorkspaceId(ticket, params),
                path,
                contentBase64,
                agentConfigWorktreeId(ticket, params));
    }

    private Object agentConfigUploadBegin(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            Map<String, ActiveUpload> activeUploads) {
        requireUploadCapacity(activeUploads);
        AgentUploadContext context = agentUploadContext(ticket, params, true);
        WorkspaceFileUpload upload;
        if (SCOPE_PUBLIC.equals(context.scope())) {
            upload = agentConfigService.beginPublicAgentFileUpload(
                    context.path(),
                    requiredNonNegativeLong(params, "size"),
                    context.worktreeId(),
                    ticketUserId(ticket));
        } else {
            upload = agentConfigService.beginWorkspaceAgentFileUpload(
                    context.workspaceId(),
                    context.path(),
                    requiredNonNegativeLong(params, "size"),
                    context.worktreeId());
        }
        return registerUpload(
                activeUploads,
                new ActiveUpload(
                        UploadKind.AGENT_CONFIG,
                        upload,
                        context.path(),
                        context.workspaceId(),
                        context.scope(),
                        context.worktreeId()));
    }

    private Object agentConfigUploadChunk(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            Map<String, ActiveUpload> activeUploads) {
        String uploadId = requiredText(params, "uploadId");
        ActiveUpload active = requireUpload(activeUploads, uploadId, UploadKind.AGENT_CONFIG);
        AgentUploadContext context = agentUploadContext(ticket, params, false);
        requireUploadContext(active, context.workspaceId(), context.scope(), context.worktreeId());
        try {
            active.upload().append(requiredNonNegativeLong(params, "index"), text(params, "contentBase64"));
            return Map.of(
                    "uploadedBytes", active.upload().uploadedBytes(),
                    "totalBytes", active.upload().expectedBytes());
        } catch (RuntimeException exception) {
            failUpload(activeUploads, uploadId, active);
            throw exception;
        }
    }

    private Object agentConfigUploadComplete(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            Map<String, ActiveUpload> activeUploads) {
        String uploadId = requiredText(params, "uploadId");
        ActiveUpload active = requireUpload(activeUploads, uploadId, UploadKind.AGENT_CONFIG);
        AgentUploadContext context = agentUploadContext(ticket, params, false);
        requireUploadContext(active, context.workspaceId(), context.scope(), context.worktreeId());
        activeUploads.remove(uploadId);
        try {
            return Map.of("size", active.upload().complete());
        } catch (RuntimeException exception) {
            active.upload().abort();
            throw exception;
        }
    }

    private void agentConfigUploadAbort(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            Map<String, ActiveUpload> activeUploads) {
        String uploadId = requiredText(params, "uploadId");
        ActiveUpload active = requireUpload(activeUploads, uploadId, UploadKind.AGENT_CONFIG);
        AgentUploadContext context = agentUploadContext(ticket, params, false);
        requireUploadContext(active, context.workspaceId(), context.scope(), context.worktreeId());
        activeUploads.remove(uploadId);
        active.upload().abort();
    }

    /** 每个分片请求都重新核对 ticket 的 scope/workspace/worktree 与角色，不能只信 begin 阶段。 */
    private AgentUploadContext agentUploadContext(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            boolean requirePath) {
        String scope = agentConfigScope(ticket, params);
        String worktreeId = agentConfigWorktreeId(ticket, params);
        String path = requirePath ? requiredText(params, "path") : null;
        if (SCOPE_PUBLIC.equals(scope)) {
            if (!ticket.superAdmin()) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
            }
            return new AgentUploadContext(scope, null, worktreeId, path);
        }
        if (!ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用 Agent 配置仅应用管理员可编辑");
        }
        return new AgentUploadContext(
                scope,
                agentConfigWorkspaceId(ticket, params),
                worktreeId,
                path);
    }

    private void agentConfigRename(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        if (SCOPE_PUBLIC.equals(scope)) {
            if (!ticket.superAdmin()) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
            }
            agentConfigService.renamePublicAgentFile(
                    requiredText(params, "path"),
                    requiredText(params, "name"),
                    agentConfigWorktreeId(ticket, params),
                    ticketUserId(ticket));
            return;
        }
        if (!ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用 Agent 配置仅应用管理员可编辑");
        }
        agentConfigService.renameWorkspaceAgentFile(
                agentConfigWorkspaceId(ticket, params),
                requiredText(params, "path"),
                requiredText(params, "name"),
                agentConfigWorktreeId(ticket, params));
    }

    private void agentConfigDelete(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        String path = requiredText(params, "path");
        if (SCOPE_PUBLIC.equals(scope)) {
            if (!ticket.superAdmin()) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
            }
            agentConfigService.deletePublicAgentFile(
                    path,
                    agentConfigWorktreeId(ticket, params),
                    ticketUserId(ticket));
            return;
        }
        if (!ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用 Agent 配置仅应用管理员可编辑");
        }
        agentConfigService.deleteWorkspaceAgentFile(
                agentConfigWorkspaceId(ticket, params),
                path,
                agentConfigWorktreeId(ticket, params));
    }

    private String agentConfigScope(WorkspaceFileSocketTicket ticket, JsonNode params) {
        if (!MODE_AGENT_CONFIG.equals(ticket.mode())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前 ticket 不允许操作 Agent 配置文件");
        }
        String ticketScope = ticket.scope();
        if (!SCOPE_PUBLIC.equals(ticketScope) && !SCOPE_WORKSPACE.equals(ticketScope)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Agent 配置文件 ticket 无效");
        }
        String requestedScope = text(params, "scope");
        if (requestedScope != null && !requestedScope.isBlank() && !ticketScope.equals(requestedScope.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Agent 配置 scope 与文件 WebSocket ticket 不匹配");
        }
        return ticketScope;
    }

    private String agentConfigWorkspaceId(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String workspaceId = text(params, "workspaceId");
        if (workspaceId == null || workspaceId.isBlank()) {
            workspaceId = ticket.workspaceId();
        }
        if (workspaceId == null || workspaceId.isBlank() || !workspaceId.equals(ticket.workspaceId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Agent 配置 workspace 与文件 WebSocket ticket 不匹配");
        }
        return workspaceId;
    }

    private String agentConfigWorktreeId(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String requested = normalizeOptional(text(params, "worktreeId"));
        String bound = normalizeOptional(ticket.worktreeId());
        if (bound == null && requested == null) {
            return null;
        }
        if (bound != null && bound.equals(requested)) {
            return bound;
        }
        throw new PlatformException(ErrorCode.FORBIDDEN, "Agent 配置 worktree 与文件 WebSocket ticket 不匹配");
    }

    private void requireUploadCapacity(Map<String, ActiveUpload> activeUploads) {
        if (activeUploads.size() >= MAX_ACTIVE_UPLOADS) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "同一文件连接的并发上传过多",
                    Map.of("maxActiveUploads", MAX_ACTIVE_UPLOADS));
        }
    }

    private Object registerUpload(Map<String, ActiveUpload> activeUploads, ActiveUpload active) {
        String uploadId;
        do {
            uploadId = "upl_" + UUID.randomUUID().toString().replace("-", "");
        } while (activeUploads.containsKey(uploadId));
        activeUploads.put(uploadId, active);
        return Map.of(
                "uploadId", uploadId,
                "chunkBytes", active.upload().chunkBytes(),
                "totalBytes", active.upload().expectedBytes());
    }

    private ActiveUpload requireUpload(
            Map<String, ActiveUpload> activeUploads,
            String uploadId,
            UploadKind expectedKind) {
        ActiveUpload active = activeUploads.get(uploadId);
        if (active == null || active.kind() != expectedKind) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "上传会话不存在或已结束",
                    Map.of("uploadId", uploadId));
        }
        return active;
    }

    private void requireUploadContext(
            ActiveUpload active,
            String workspaceId,
            String scope,
            String worktreeId) {
        if (!Objects.equals(active.workspaceId(), workspaceId)
                || !Objects.equals(active.scope(), scope)
                || !Objects.equals(active.worktreeId(), worktreeId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "上传会话与文件 WebSocket 上下文不匹配");
        }
    }

    private void failUpload(
            Map<String, ActiveUpload> activeUploads,
            String uploadId,
            ActiveUpload active) {
        activeUploads.remove(uploadId);
        active.upload().abort();
    }

    private void abortUploads(Map<String, ActiveUpload> activeUploads) {
        activeUploads.values().forEach(active -> active.upload().abort());
        activeUploads.clear();
    }

    private WorkspaceId workspaceId(WorkspaceFileSocketTicket ticket, JsonNode params) {
        if (!MODE_WORKSPACE.equals(ticket.mode())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前 ticket 不允许操作工作区文件");
        }
        String workspaceId = text(params, "workspaceId");
        if (workspaceId == null || workspaceId.isBlank()) {
            workspaceId = ticket.workspaceId();
        }
        if (workspaceId == null || workspaceId.isBlank() || !workspaceId.equals(ticket.workspaceId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Workspace 与文件 WebSocket ticket 不匹配");
        }
        return new WorkspaceId(workspaceId);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String success(String id, Object data, String traceId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", id);
        envelope.put("type", "result");
        envelope.put("data", data);
        envelope.put("traceId", traceId);
        return write(envelope);
    }

    private String error(String id, String code, String message, String traceId, Map<String, Object> details) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", id);
        envelope.put("type", "error");
        envelope.put("code", code);
        envelope.put("message", message);
        envelope.put("traceId", traceId);
        envelope.put("details", details == null ? Map.of() : details);
        return write(envelope);
    }

    private Mono<Void> sendErrorAndClose(
            WebSocketSession session,
            String id,
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
        return session.send(Mono.just(session.textMessage(error(id, code, message, traceId, details))))
                .then(session.close());
    }

    private String write(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            return "{\"type\":\"error\",\"code\":\"INTERNAL_ERROR\",\"message\":\"文件 WebSocket 响应序列化失败\"}";
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 不能为空");
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private long requiredNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 必须是非负整数");
        }
        return value.longValue();
    }

    private Long optionalNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 必须是非负整数");
        }
        return value.longValue();
    }

    private String query(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && key.equals(pair[0])) {
                return pair[1];
            }
        }
        return "";
    }

    private String traceId(HttpHeaders headers) {
        return TraceIdSupport.resolve(headers.getFirst(TraceConstants.TRACE_ID_HEADER));
    }

    private enum UploadKind {
        WORKSPACE,
        AGENT_CONFIG
    }

    private record ActiveUpload(
            UploadKind kind,
            WorkspaceFileUpload upload,
            String path,
            String workspaceId,
            String scope,
            String worktreeId) {}

    private record AgentUploadContext(
            String scope,
            String workspaceId,
            String worktreeId,
            String path) {}
}
