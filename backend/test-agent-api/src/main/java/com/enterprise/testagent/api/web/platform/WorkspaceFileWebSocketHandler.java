package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.observability.TraceIdSupport;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.ApplicationAutomationReferenceWorkspaceReconciliationService;
import com.enterprise.testagent.workspace.AgentSkillHubApplicationService;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import com.enterprise.testagent.workspace.WorkspaceDirectoryService;
import com.enterprise.testagent.workspace.WorkspaceFileUpload;
import com.enterprise.testagent.workspace.FileTreeEntryResponse;
import com.enterprise.testagent.workspace.FileSearchResultResponse;
import com.enterprise.testagent.workspace.WorkspaceViewApplicationService;
import com.enterprise.testagent.workspace.WorkspaceViewLocator;
import com.enterprise.testagent.workspace.WorkspaceViewLocatorKind;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientWorkspaceFileGateway;
import com.enterprise.testagent.workspace.RequirementImportApplicationService;
import com.enterprise.testagent.system.supportaccess.SupportAccessAuthorization;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
import reactor.core.publisher.Flux;
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
    private static final String MODE_AGENT_SKILL_HUB = "agent-skill-hub";
    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_WORKSPACE = "WORKSPACE";
    private static final int MAX_ACTIVE_UPLOADS = 4;

    private final WorkspaceFileSocketTicketService ticketService;
    private final WorkspaceApplicationService workspaceService;
    private final WorkspaceDirectoryService directoryService;
    private final AgentConfigApplicationService agentConfigService;
    private final AgentSkillHubApplicationService agentSkillHubService;
    private final WorkspaceViewApplicationService workspaceViewService;
    private final ManagedWorkspacePathResolver pathResolver;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;
    private final boolean allowAnyOrigin;
    private LocalClientWorkspaceFileGateway localClientFileGateway;
    private RequirementImportApplicationService requirementImportService;
    private ApplicationAutomationReferenceWorkspaceReconciliationService automationReferenceReconciliationService;

    /** 需求导入为可选 setter 注入，保持既有 handler 单元测试构造器兼容。 */
    @Autowired
    void setRequirementImportService(RequirementImportApplicationService requirementImportService) {
        this.requirementImportService = requirementImportService;
    }

    /** 自动化引用对账复用 Agent 配置文件通道；直接构造的旧单元测试无需装配该可选操作。 */
    @Autowired
    void setAutomationReferenceReconciliationService(
            ApplicationAutomationReferenceWorkspaceReconciliationService reconciliationService) {
        this.automationReferenceReconciliationService = Objects.requireNonNull(reconciliationService);
    }

    /**
     * 装配文件 WebSocket handler 依赖和 Origin 白名单。
     */
    @Autowired
    WorkspaceFileWebSocketHandler(
            WorkspaceFileSocketTicketService ticketService,
            WorkspaceApplicationService workspaceService,
            WorkspaceDirectoryService directoryService,
            AgentConfigApplicationService agentConfigService,
            AgentSkillHubApplicationService agentSkillHubService,
            WorkspaceViewApplicationService workspaceViewService,
            ManagedWorkspacePathResolver pathResolver,
            ObjectMapper objectMapper,
            @Value("${test-agent.security.cors-allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:4177,http://127.0.0.1:4177,http://localhost:4187,http://127.0.0.1:4187,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
            String allowedOrigins) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService must not be null");
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
        this.directoryService = Objects.requireNonNull(directoryService, "directoryService must not be null");
        this.agentConfigService = Objects.requireNonNull(agentConfigService, "agentConfigService must not be null");
        this.agentSkillHubService = Objects.requireNonNull(agentSkillHubService, "agentSkillHubService must not be null");
        this.workspaceViewService = Objects.requireNonNull(workspaceViewService, "workspaceViewService must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.allowedOrigins = Set.copyOf(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        this.allowAnyOrigin = this.allowedOrigins.size() == 1 && this.allowedOrigins.contains("*");
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
        this.agentSkillHubService = null;
        this.workspaceViewService = null;
        this.pathResolver = ManagedWorkspacePathResolver.legacyOnly();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.allowedOrigins = Set.copyOf(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        this.allowAnyOrigin = this.allowedOrigins.size() == 1 && this.allowedOrigins.contains("*");
    }

    /** 兼容既有组合视图单元测试构造路径；Hub RPC 测试应使用生产形状构造器显式注入服务。 */
    WorkspaceFileWebSocketHandler(
            WorkspaceFileSocketTicketService ticketService,
            WorkspaceApplicationService workspaceService,
            WorkspaceDirectoryService directoryService,
            AgentConfigApplicationService agentConfigService,
            WorkspaceViewApplicationService workspaceViewService,
            ObjectMapper objectMapper,
            String allowedOrigins) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService must not be null");
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
        this.directoryService = Objects.requireNonNull(directoryService, "directoryService must not be null");
        this.agentConfigService = Objects.requireNonNull(agentConfigService, "agentConfigService must not be null");
        this.agentSkillHubService = null;
        this.workspaceViewService = Objects.requireNonNull(workspaceViewService, "workspaceViewService must not be null");
        this.pathResolver = ManagedWorkspacePathResolver.legacyOnly();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.allowedOrigins = Set.copyOf(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        this.allowAnyOrigin = this.allowedOrigins.size() == 1 && this.allowedOrigins.contains("*");
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
            if (!originAllowed(origin)) {
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
        Set<String> activeLocalUploads = ConcurrentHashMap.newKeySet();
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(payload -> Mono.fromCallable(() -> handleMessage(
                                activeTicket, payload, traceId, activeUploads, activeLocalUploads))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(outbound::tryEmitNext)
                        .then())
                .doFinally(ignored -> {
                    abortUploads(activeUploads);
                    scheduleAbortLocalUploads(activeTicket, activeLocalUploads, traceId);
                    outbound.tryEmitComplete();
                })
                .then();
        Mono<Void> sender = session.send(outbound.asFlux().map(session::textMessage));
        Mono<Void> authorization = activeTicket.sharedSession()
                ? Flux.interval(java.time.Duration.ofSeconds(1))
                        .publishOn(Schedulers.boundedElastic())
                        .doOnNext(ignored -> ticketService.authorizeWorkspaceRpc(
                                activeTicket, new WorkspaceId(activeTicket.workspaceId())))
                        .then()
                        .onErrorResume(RuntimeException.class, failure -> {
                            String errorCode = failure instanceof PlatformException platform
                                    ? platform.errorCode().name()
                                    : ErrorCode.INTERNAL_ERROR.name();
                            try {
                                ticketService.recordSharedRpc(
                                        activeTicket, "workspace.authorization",
                                        new WorkspaceId(activeTicket.workspaceId()), null,
                                        failure instanceof PlatformException ? "DENIED" : "FAILED",
                                        errorCode, traceId);
                            } catch (RuntimeException ignored) {
                                // 授权与审计同时失败时仍优先断开连接，禁止继续使用旧 ticket。
                            }
                            outbound.tryEmitNext(error(
                                    null, errorCode, "分享文件授权已失效", traceId, Map.of()));
                            outbound.tryEmitComplete();
                            abortUploads(activeUploads);
                            scheduleAbortLocalUploads(activeTicket, activeLocalUploads, traceId);
                            return session.close();
                        })
                : Mono.never();
        return Mono.firstWithSignal(Mono.when(inbound, sender), authorization)
                .doFinally(ignored -> {
                    abortUploads(activeUploads);
                    scheduleAbortLocalUploads(activeTicket, activeLocalUploads, traceId);
                });
    }

    /**
     * 单独配置通配符时仍校验浏览器 Origin 的协议、主机和 URI 结构，避免把缺失或畸形来源放进文件通道。
     */
    private boolean originAllowed(String origin) {
        if (!allowAnyOrigin) {
            return allowedOrigins.contains(origin);
        }
        try {
            AppSourceWebSocketOrigin.canonicalize(origin);
            return true;
        } catch (PlatformException exception) {
            return false;
        }
    }

    private String handleMessage(
            WorkspaceFileSocketTicket ticket,
            String payload,
            String traceId,
            Map<String, ActiveUpload> activeUploads,
            Set<String> activeLocalUploads) {
        String id = null;
        String op = null;
        WorkspaceId auditedWorkspaceId = null;
        String auditedPath = null;
        SupportAccessAuthorization supportAuthorization = null;
        try {
            JsonNode root = objectMapper.readTree(payload);
            id = text(root, "id");
            op = requiredText(root, "op");
            JsonNode params = root.path("params");
            boolean experienceWorkspaceRpc = false;
            if (MODE_WORKSPACE.equals(ticket.mode()) && op.startsWith("workspace.")) {
                if (ticket.supportReadOnly()) {
                    requireSupportReadOperation(op);
                }
                auditedWorkspaceId = workspaceId(ticket, params);
                supportAuthorization = authorizeWorkspaceRpc(ticket, auditedWorkspaceId);
                // 实时授权必须先于路径级拒绝，确保体验资格、当前绑定和服务器事实每条 RPC 都重新核对。
                auditedPath = supportAuditPath(op, params);
                experienceWorkspaceRpc = ExperienceWorkspaceAccessAuthorizer
                        .isExperienceWorkspaceId(auditedWorkspaceId);
                if (experienceWorkspaceRpc) {
                    requireExperienceWorkspaceOperation(op, params);
                }
            }
            if (ticket.localClient()) {
                Object data = handleLocalClientMessage(
                        ticket, op, params, traceId, activeLocalUploads);
                return success(id, data, traceId);
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
                case "workspace.read.binary.chunk" -> workspaceService.readFileBinaryChunk(
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
                case "workspace.resolve-physical-path" -> resolvePhysicalPath(ticket, params);
                case "workspace.requirement-import-items" -> requirementImportItems(ticket, params);
                case "workspace.requirement-import" -> importRequirements(ticket, params);
                case "workspace.view.list" -> workspaceViewService.list(
                        ticketUserId(ticket),
                        workspaceId(ticket, params),
                        viewLocator(params));
                case "workspace.view.read" -> workspaceViewService.read(
                        ticketUserId(ticket),
                        workspaceId(ticket, params),
                        viewLocator(params));
                case "workspace.view.read.chunk" -> workspaceViewService.readChunk(
                        ticketUserId(ticket),
                        workspaceId(ticket, params),
                        viewLocator(params),
                        requiredNonNegativeLong(params, "offset"),
                        optionalNonNegativeLong(params, "expectedSize"),
                        optionalNonNegativeLong(params, "expectedLastModifiedMillis"));
                case "workspace.view.read.binary.chunk" -> workspaceViewService.readBinaryChunk(
                        ticketUserId(ticket),
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
                case "agent-config.automation-reference.reconcile" ->
                        agentConfigAutomationReferenceReconcile(ticket, params, traceId);
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
                case "agent-config.copy" -> {
                    agentConfigCopy(ticket, params);
                    yield null;
                }
                case "agent-config.move" -> {
                    agentConfigMove(ticket, params);
                    yield null;
                }
                case "agent-config.delete" -> {
                    agentConfigDelete(ticket, params);
                    yield null;
                }
                case "hub.asset.read" -> hubAssetRead(ticket, params);
                case "hub.reference.create" -> hubReferenceCreate(ticket, params);
                case "hub.reference.remove" -> hubReferenceRemove(ticket, params);
                case "hub.reference.update.start" -> hubReferenceUpdateStart(ticket, params);
                case "hub.reference.update.read-conflict" -> hubReferenceUpdateReadConflict(ticket, params);
                case "hub.reference.update.resolve" -> hubReferenceUpdateResolve(ticket, params);
                case "hub.reference.update.complete" -> hubReferenceUpdateComplete(ticket, params);
                case "hub.reference.update.abort" -> {
                    hubReferenceUpdateAbort(ticket, params);
                    yield null;
                }
                case "directory.list" -> directoryList(ticket, params);
                case "workspace.create" -> createWorkspace(ticket, params, traceId);
                default -> throw new PlatformException(ErrorCode.VALIDATION_ERROR, "不支持的文件 WebSocket 操作", Map.of("op", op));
            };
            if (experienceWorkspaceRpc) {
                data = sanitizeExperienceReadResult(op, data);
            }
            if (supportAuthorization != null) {
                data = sanitizeSupportReadResult(op, data);
                // 审计必须先于正文响应落库；审计存储异常时不会把 data 发送给浏览器。
                ticketService.recordSupportRpc(
                        supportAuthorization, op, auditedWorkspaceId, auditedPath, "SUCCESS", null, traceId);
            }
            if (ticket.sharedSession()) {
                ticketService.recordSharedRpc(
                        ticket, op, sharedAuditWorkspace(ticket, auditedWorkspaceId), auditedPath,
                        "SUCCESS", null, traceId);
            }
            return success(id, data, traceId);
        } catch (PlatformException exception) {
            if (supportAuthorization != null) {
                try {
                    ticketService.recordSupportRpc(
                            supportAuthorization,
                            op == null ? "workspace.unknown" : op,
                            auditedWorkspaceId,
                            auditedPath,
                            "FAILED",
                            exception.errorCode().name(),
                            traceId);
                } catch (RuntimeException auditFailure) {
                    return error(id, ErrorCode.INTERNAL_ERROR.name(), "排查访问审计失败", traceId, Map.of());
                }
            }
            if (ticket.sharedSession()) {
                try {
                    ticketService.recordSharedRpc(
                            ticket,
                            op == null ? "workspace.unknown" : op,
                            sharedAuditWorkspace(ticket, auditedWorkspaceId),
                            auditedPath,
                            "DENIED",
                            exception.errorCode().name(),
                            traceId);
                } catch (RuntimeException auditFailure) {
                    return error(id, ErrorCode.INTERNAL_ERROR.name(), "分享文件审计失败", traceId, Map.of());
                }
            }
            return error(id, exception.errorCode().name(), exception.getMessage(), traceId, exception.details());
        } catch (Exception exception) {
            if (supportAuthorization != null) {
                try {
                    ticketService.recordSupportRpc(
                            supportAuthorization,
                            op == null ? "workspace.unknown" : op,
                            auditedWorkspaceId,
                            auditedPath,
                            "FAILED",
                            ErrorCode.INTERNAL_ERROR.name(),
                            traceId);
                } catch (RuntimeException ignored) {
                    // 两次失败均只返回稳定错误，不暴露审计存储或文件系统异常细节。
                }
            }
            if (ticket.sharedSession()) {
                try {
                    ticketService.recordSharedRpc(
                            ticket,
                            op == null ? "workspace.unknown" : op,
                            sharedAuditWorkspace(ticket, auditedWorkspaceId),
                            auditedPath,
                            "FAILED",
                            ErrorCode.INTERNAL_ERROR.name(),
                            traceId);
                } catch (RuntimeException ignored) {
                    // 审计与文件处理均失败时只返回稳定错误，不泄露任一内部异常。
                }
            }
            return error(id, ErrorCode.VALIDATION_ERROR.name(), "文件 WebSocket 消息无效", traceId, Map.of());
        }
    }

    private WorkspaceId sharedAuditWorkspace(
            WorkspaceFileSocketTicket ticket,
            WorkspaceId requestedWorkspaceId) {
        return requestedWorkspaceId != null
                ? requestedWorkspaceId
                : new WorkspaceId(ticket.workspaceId());
    }

    /** 本地 ticket 只开放目录选择和完整基础文件能力，不开放 Git、Agent 配置、附件或组合视图。 */
    private Object handleLocalClientMessage(
            WorkspaceFileSocketTicket ticket,
            String op,
            JsonNode params,
            String traceId,
            Set<String> activeLocalUploads) {
        if (localClientFileGateway == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "本地文件隧道未装配");
        }
        if (MODE_DIRECTORY_PICKER.equals(ticket.mode())) {
            if (!Set.of("directory.list").contains(op)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "本地目录选择 ticket 不允许该操作");
            }
            return localClientFileGateway.invoke(
                    ticket.localClientInstanceId(),
                    ticket.connectionGeneration(),
                    null,
                    null,
                    op,
                    params,
                    traceId);
        }
        if (!MODE_WORKSPACE.equals(ticket.mode()) || !LOCAL_WORKSPACE_OPERATIONS.contains(op)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "本地工作区首版不允许该文件操作",
                    Map.of("op", op));
        }
        WorkspaceId workspaceId = workspaceId(ticket, params);
        requireLocalWorkspaceWriteIfNeeded(ticket, workspaceId, op, params);
        String uploadId = localUploadId(op, params);
        if ("workspace.upload.begin".equals(op)) {
            requireLocalUploadCapacity(activeLocalUploads);
        } else if (uploadId != null && !activeLocalUploads.contains(uploadId)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "上传会话不存在或不属于当前文件连接");
        }
        if ("workspace.upload.complete".equals(op) || "workspace.upload.abort".equals(op)) {
            activeLocalUploads.remove(uploadId);
        }
        try {
            JsonNode result = localClientFileGateway.invoke(
                    ticket.localClientInstanceId(),
                    ticket.connectionGeneration(),
                    workspaceId.value(),
                    ticket.rootDigest(),
                    op,
                    params,
                    traceId);
            if ("workspace.upload.begin".equals(op)) {
                String registeredUploadId = result == null ? null : text(result, "uploadId");
                if (registeredUploadId == null || registeredUploadId.isBlank()) {
                    throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地上传会话响应无效");
                }
                activeLocalUploads.add(registeredUploadId);
            }
            return result;
        } catch (RuntimeException exception) {
            if (uploadId != null) {
                activeLocalUploads.remove(uploadId);
                abortLocalUpload(ticket, workspaceId.value(), uploadId, traceId);
            }
            throw exception;
        }
    }

    private String localUploadId(String operation, JsonNode params) {
        return switch (operation) {
            case "workspace.upload.chunk", "workspace.upload.complete", "workspace.upload.abort" ->
                    requiredText(params, "uploadId");
            default -> null;
        };
    }

    private static void requireLocalUploadCapacity(Set<String> activeLocalUploads) {
        if (activeLocalUploads.size() >= MAX_ACTIVE_UPLOADS) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "同一文件连接的并发上传过多",
                    Map.of("maxActiveUploads", MAX_ACTIVE_UPLOADS));
        }
    }

    private void abortLocalUpload(
            WorkspaceFileSocketTicket ticket,
            String workspaceId,
            String uploadId,
            String traceId) {
        try {
            localClientFileGateway.abortUpload(
                    ticket.localClientInstanceId(),
                    ticket.connectionGeneration(),
                    workspaceId,
                    ticket.rootDigest(),
                    uploadId,
                    traceId);
        } catch (RuntimeException ignored) {
            // 连接已经断开时客户端会在 WebSocket cleanup 中统一 abortAll；这里保持清理幂等。
        }
    }

    private void scheduleAbortLocalUploads(
            WorkspaceFileSocketTicket ticket,
            Set<String> activeLocalUploads,
            String traceId) {
        if (!ticket.localClient()) {
            return;
        }
        String uploadId;
        while ((uploadId = activeLocalUploads.stream().findFirst().orElse(null)) != null) {
            if (!activeLocalUploads.remove(uploadId)) {
                continue;
            }
            String currentUploadId = uploadId;
            Mono.fromRunnable(() -> abortLocalUpload(
                            ticket, ticket.workspaceId(), currentUploadId, traceId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        }
    }

    private static final Set<String> LOCAL_WORKSPACE_OPERATIONS = Set.of(
            "workspace.list",
            "workspace.search",
            "workspace.read",
            "workspace.read.chunk",
            "workspace.read.binary.chunk",
            "workspace.write",
            "workspace.upload",
            "workspace.upload.begin",
            "workspace.upload.chunk",
            "workspace.upload.complete",
            "workspace.upload.abort",
            "workspace.copy",
            "workspace.move",
            "workspace.rename",
            "workspace.status",
            "workspace.delete",
            "workspace.mkdir");

    private void requireLocalWorkspaceWriteIfNeeded(
            WorkspaceFileSocketTicket ticket,
            WorkspaceId workspaceId,
            String op,
            JsonNode params) {
        switch (op) {
            case "workspace.write", "workspace.upload", "workspace.upload.begin",
                    "workspace.rename", "workspace.delete", "workspace.mkdir" ->
                    requireWorkspaceWrite(ticket, workspaceId, requiredText(params, "path"));
            case "workspace.copy", "workspace.move" -> {
                requireWorkspaceWrite(ticket, workspaceId, requiredText(params, "sourcePath"));
                requireWorkspaceWrite(ticket, workspaceId, requiredText(params, "targetPath"));
            }
            case "workspace.upload.chunk", "workspace.upload.complete", "workspace.upload.abort" ->
                    workspaceService.requireWorkspaceWriteAccess(
                            workspaceId,
                            new com.enterprise.testagent.domain.user.UserId(ticket.userId()),
                            ticket.appAdmin());
            default -> {
                // 只读操作已由 ticketService.authorizeWorkspaceRpc 完成逐条 generation 与归属校验。
            }
        }
    }

    /** 可选 setter 保持既有 handler 单元测试构造器稳定。 */
    @Autowired(required = false)
    void configureLocalClientFileGateway(LocalClientWorkspaceFileGateway localClientFileGateway) {
        this.localClientFileGateway = Objects.requireNonNull(
                localClientFileGateway, "localClientFileGateway must not be null");
    }

    /** 排查 ticket 只开放有限文件读取白名单，所有写入、Git、配置和组合视图操作均拒绝。 */
    private void requireSupportReadOperation(String op) {
        if (!Set.of(
                "workspace.list",
                "workspace.search",
                "workspace.read",
                "workspace.read.chunk",
                "workspace.read.binary.chunk").contains(op)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "排查只读 ticket 不允许该文件操作", Map.of("op", op));
        }
    }

    private String supportAuditPath(String op, JsonNode params) {
        if ("workspace.search".equals(op)) {
            return null;
        }
        String path = text(params, "path");
        if (protectedConfigPath(path)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "排查入口不允许读取 Agent 配置文件");
        }
        return path;
    }

    /** 根目录和搜索结果过滤 .opencode，避免通过普通 workspace 读绕过 Agent 配置边界。 */
    private Object sanitizeSupportReadResult(String op, Object data) {
        if ("workspace.list".equals(op) && data instanceof java.util.List<?> values) {
            return values.stream()
                    .filter(value -> !(value instanceof FileTreeEntryResponse entry) || !protectedConfigPath(entry.path()))
                    .toList();
        }
        if ("workspace.search".equals(op) && data instanceof java.util.List<?> values) {
            return values.stream()
                    .filter(value -> !(value instanceof FileSearchResultResponse entry) || !protectedConfigPath(entry.path()))
                    .toList();
        }
        return data;
    }

    /**
     * 体验区的普通文件通道不能借管理员角色触达 Git 元数据、受控 Agent 配置或应用引用组合视图。
     * 直接路径在执行前拒绝，根列表与搜索结果在响应前过滤，双层约束避免目录名侧漏后再被别名访问。
     */
    private void requireExperienceWorkspaceOperation(String op, JsonNode params) {
        if (op.startsWith("workspace.view.")) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "体验工作区不支持应用引用目录");
        }
        List<String> paths = new java.util.ArrayList<>();
        paths.add(text(params, "path"));
        paths.add(text(params, "sourcePath"));
        paths.add(text(params, "targetPath"));
        if ("workspace.rename".equals(op)) {
            paths.add(text(params, "name"));
        }
        if (paths.stream().anyMatch(this::experienceProtectedPath)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "体验工作区不允许访问受控目录");
        }
    }

    /** 体验区列表和搜索不返回 .git 或 .opencode 命名空间。 */
    private Object sanitizeExperienceReadResult(String op, Object data) {
        if ("workspace.list".equals(op) && data instanceof java.util.List<?> values) {
            return values.stream()
                    .filter(value -> !(value instanceof FileTreeEntryResponse entry)
                            || !experienceProtectedPath(entry.path()))
                    .toList();
        }
        if ("workspace.search".equals(op) && data instanceof java.util.List<?> values) {
            return values.stream()
                    .filter(value -> !(value instanceof FileSearchResultResponse entry)
                            || !experienceProtectedPath(entry.path()))
                    .toList();
        }
        return data;
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

    private SupportAccessAuthorization authorizeWorkspaceRpc(
            WorkspaceFileSocketTicket ticket,
            WorkspaceId workspaceId) {
        return ticketService.authorizeWorkspaceRpc(ticket, workspaceId);
    }

    private String resolvePhysicalPath(WorkspaceFileSocketTicket ticket, JsonNode params) {
        WorkspaceId workspaceId = workspaceId(ticket, params);
        if (ticket.supportReadOnly() || ticket.sharedSession() || ticket.appSourceWorkspace()
                || ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspaceId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前会话不允许解析物理路径");
        }
        return workspaceService.resolvePhysicalFilePath(workspaceId, requiredText(params, "path"));
    }

    private Object importRequirements(WorkspaceFileSocketTicket ticket, JsonNode params) {
        WorkspaceId workspaceId = workspaceId(ticket, params);
        if (ticket.supportReadOnly() || ticket.sharedSession() || ticket.appSourceWorkspace()
                || ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspaceId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前工作区不允许导入需求");
        }
        if (requirementImportService == null || ticket.unifiedAuthId() == null || ticket.unifiedAuthId().isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "需求导入服务不可用");
        }
        requireWorkspaceWrite(ticket, workspaceId, "spec");
        return requirementImportService.importRequirements(
                ticket.unifiedAuthId(),
                new RequirementImportApplicationService.ImportCommand(
                        workspaceId.value(),
                        requiredText(params, "appShortName"),
                        requiredText(params, "editionId"),
                        requiredStringList(params, "selectedSubItemNos"),
                        requiredText(params, "requestId")));
    }

    private Object requirementImportItems(WorkspaceFileSocketTicket ticket, JsonNode params) {
        WorkspaceId workspaceId = workspaceId(ticket, params);
        if (ticket.supportReadOnly() || ticket.sharedSession() || ticket.appSourceWorkspace()
                || ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspaceId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前工作区不允许读取需求导入状态");
        }
        if (requirementImportService == null || ticket.unifiedAuthId() == null || ticket.unifiedAuthId().isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "需求导入服务不可用");
        }
        requireWorkspaceWrite(ticket, workspaceId, "spec");
        return requirementImportService.listWorkspaceItems(
                ticket.unifiedAuthId(),
                workspaceId.value(),
                requiredText(params, "appShortName"),
                requiredText(params, "editionId"));
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
        return new WorkspaceViewLocator(
                kind,
                text(locator, "path"),
                text(locator, "referenceAlias"),
                text(locator, "automationAppId"),
                text(locator, "automationRepositoryId"),
                optionalNonNegativeLong(locator, "automationGeneration"),
                text(locator, "automationReadLease"));
    }

    private void requireWorkspaceWrite(WorkspaceFileSocketTicket ticket, WorkspaceId workspaceId, String path) {
        if (ticket.sharedSession() && !ticket.shareCanChat()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前分享成员仅可查看工作区文件");
        }
        if (ticket.userId() != null) {
            workspaceService.requireWorkspaceWriteAccess(
                    workspaceId,
                    new com.enterprise.testagent.domain.user.UserId(ticket.userId()),
                    ticket.appAdmin());
        }
        if (ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspaceId)
                && experienceProtectedPath(path)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "体验工作区不允许访问受控目录");
        }
        if (protectedConfigPath(path) && !ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用 OpenCode 配置仅应用管理员可编辑");
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
        // 整个命名空间都属于应用配置，不能让 command/plugin 或辅助源码通过目录别名绕过管理员权限。
        return normalized.equals(".opencode") || normalized.startsWith(".opencode/");
    }

    private boolean experienceProtectedPath(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        try {
            normalized = java.nio.file.Path.of(normalized).normalize().toString().replace('\\', '/');
        } catch (RuntimeException exception) {
            return true;
        }
        // 体验目录多人共享，任意层级的 Git 元数据和 OpenCode 配置命名空间都不通过文件 RPC 暴露。
        return java.util.Arrays.stream(normalized.split("/"))
                .anyMatch(segment -> ".git".equalsIgnoreCase(segment) || ".opencode".equalsIgnoreCase(segment));
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
        return RuntimeDtos.WorkspaceResponse.from(workspace, pathResolver);
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

    private Object agentConfigAutomationReferenceReconcile(
            WorkspaceFileSocketTicket ticket,
            JsonNode params,
            String traceId) {
        if (!SCOPE_WORKSPACE.equals(agentConfigScope(ticket, params))) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "自动化引用只能对账当前应用工作树");
        }
        if (agentConfigWorktreeId(ticket, params) != null) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "自动化引用不能写入独立 Agent worktree");
        }
        if (automationReferenceReconciliationService == null || ticketUserId(ticket) == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "自动化引用对账服务不可用");
        }
        Workspace workspace = workspaceService.requireWorkspaceOnCurrentServer(
                new WorkspaceId(agentConfigWorkspaceId(ticket, params)), traceId);
        var result = automationReferenceReconciliationService.reconcile(
                workspace, ticketUserId(ticket), traceId);
        return Map.of(
                "changed", result.configurationChanged(),
                "warnings", result.warnings());
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

    private void agentConfigCopy(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        String sourcePath = requiredText(params, "sourcePath");
        String targetPath = requiredText(params, "targetPath");
        if (SCOPE_PUBLIC.equals(scope)) {
            if (!ticket.superAdmin()) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
            }
            agentConfigService.copyPublicAgentFile(
                    sourcePath,
                    targetPath,
                    agentConfigWorktreeId(ticket, params),
                    ticketUserId(ticket));
            return;
        }
        if (!ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用 Agent 配置仅应用管理员可编辑");
        }
        agentConfigService.copyWorkspaceAgentFile(
                agentConfigWorkspaceId(ticket, params),
                sourcePath,
                targetPath,
                agentConfigWorktreeId(ticket, params));
    }

    private void agentConfigMove(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        String sourcePath = requiredText(params, "sourcePath");
        String targetPath = requiredText(params, "targetPath");
        if (SCOPE_PUBLIC.equals(scope)) {
            if (!ticket.superAdmin()) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
            }
            agentConfigService.movePublicAgentFile(
                    sourcePath,
                    targetPath,
                    agentConfigWorktreeId(ticket, params),
                    ticketUserId(ticket));
            return;
        }
        if (!ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用 Agent 配置仅应用管理员可编辑");
        }
        agentConfigService.moveWorkspaceAgentFile(
                agentConfigWorkspaceId(ticket, params),
                sourcePath,
                targetPath,
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

    /** Hub 正文只允许使用独立只读 ticket，避免通过普通 HTTP 大对象响应旁路文件通道。 */
    private Object hubAssetRead(WorkspaceFileSocketTicket ticket, JsonNode params) {
        requireHubService();
        if (!MODE_AGENT_SKILL_HUB.equals(ticket.mode()) || !"HUB".equals(ticket.scope())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前 ticket 不允许读取 Hub 制品");
        }
        return agentSkillHubService.readFile(requiredText(params, "revisionId"), requiredText(params, "path"));
    }

    private Object hubReferenceCreate(WorkspaceFileSocketTicket ticket, JsonNode params) {
        requireHubReferenceWrite(ticket);
        return agentSkillHubService.createReference(
                requiredText(params, "assetId"), ticket.workspaceId(), text(params, "aliasTechnicalId"), ticketUserId(ticket));
    }

    private Object hubReferenceRemove(WorkspaceFileSocketTicket ticket, JsonNode params) {
        requireHubReferenceWrite(ticket);
        return agentSkillHubService.removeReference(
                requiredText(params, "assetId"), ticket.workspaceId(), ticketUserId(ticket));
    }

    private Object hubReferenceUpdateStart(WorkspaceFileSocketTicket ticket, JsonNode params) {
        requireHubReferenceWrite(ticket);
        return agentSkillHubService.startUpdate(
                requiredText(params, "referenceId"), ticket.workspaceId(), ticketUserId(ticket));
    }

    private Object hubReferenceUpdateReadConflict(WorkspaceFileSocketTicket ticket, JsonNode params) {
        requireHubReferenceWrite(ticket);
        return agentSkillHubService.getUpdateOperation(requiredText(params, "operationId"), ticketUserId(ticket));
    }

    private Object hubReferenceUpdateResolve(WorkspaceFileSocketTicket ticket, JsonNode params) {
        requireHubReferenceWrite(ticket);
        return agentSkillHubService.resolveUpdateConflict(
                requiredText(params, "operationId"), requiredText(params, "path"),
                requiredText(params, "resolution"), text(params, "content"), ticketUserId(ticket));
    }

    private Object hubReferenceUpdateComplete(WorkspaceFileSocketTicket ticket, JsonNode params) {
        requireHubReferenceWrite(ticket);
        return agentSkillHubService.completeUpdate(requiredText(params, "operationId"), ticketUserId(ticket));
    }

    private void hubReferenceUpdateAbort(WorkspaceFileSocketTicket ticket, JsonNode params) {
        requireHubReferenceWrite(ticket);
        agentSkillHubService.abortUpdate(requiredText(params, "operationId"), ticketUserId(ticket));
    }

    private void requireHubReferenceWrite(WorkspaceFileSocketTicket ticket) {
        requireHubService();
        if (!MODE_AGENT_CONFIG.equals(ticket.mode()) || !SCOPE_WORKSPACE.equals(ticket.scope())
                || ticket.workspaceId() == null || !ticket.appAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Hub 引用仅允许应用管理员写入当前个人工作区");
        }
    }

    private void requireHubService() {
        if (agentSkillHubService == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Hub 文件服务不可用");
        }
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

    private List<String> requiredStringList(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 不能为空");
        }
        List<String> values = new java.util.ArrayList<>();
        value.forEach(item -> {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 包含无效值");
            }
            values.add(item.asText().trim());
        });
        return List.copyOf(values);
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
