package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import com.icbc.testagent.workspace.AgentConfigApplicationService;
import com.icbc.testagent.workspace.WorkspaceApplicationService;
import com.icbc.testagent.workspace.WorkspaceDirectoryService;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
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

    private final WorkspaceFileSocketTicketService ticketService;
    private final WorkspaceApplicationService workspaceService;
    private final WorkspaceDirectoryService directoryService;
    private final AgentConfigApplicationService agentConfigService;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;

    /**
     * 装配文件 WebSocket handler 依赖和 Origin 白名单。
     */
    public WorkspaceFileWebSocketHandler(
            WorkspaceFileSocketTicketService ticketService,
            WorkspaceApplicationService workspaceService,
            WorkspaceDirectoryService directoryService,
            AgentConfigApplicationService agentConfigService,
            ObjectMapper objectMapper,
            @Value("${test-agent.security.cors-allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:4177,http://127.0.0.1:4177,http://localhost:4187,http://127.0.0.1:4187,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
            String allowedOrigins) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService must not be null");
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
        this.directoryService = Objects.requireNonNull(directoryService, "directoryService must not be null");
        this.agentConfigService = Objects.requireNonNull(agentConfigService, "agentConfigService must not be null");
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
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(payload -> Mono.fromCallable(() -> handleMessage(activeTicket, payload, traceId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(outbound::tryEmitNext)
                        .then())
                .doFinally(ignored -> outbound.tryEmitComplete())
                .then();
        Mono<Void> sender = session.send(outbound.asFlux().map(session::textMessage));
        return Mono.when(inbound, sender);
    }

    private String handleMessage(WorkspaceFileSocketTicket ticket, String payload, String traceId) {
        String id = null;
        try {
            JsonNode root = objectMapper.readTree(payload);
            id = text(root, "id");
            String op = requiredText(root, "op");
            JsonNode params = root.path("params");
            Object data = switch (op) {
                case "workspace.list" -> workspaceService.listFiles(workspaceId(ticket, params), text(params, "path"));
                case "workspace.search" -> workspaceService.searchFiles(workspaceId(ticket, params), text(params, "query"));
                case "workspace.read" -> workspaceService.readFile(workspaceId(ticket, params), requiredText(params, "path"));
                case "workspace.write" -> {
                    workspaceService.writeFile(workspaceId(ticket, params), requiredText(params, "path"), text(params, "content"));
                    yield null;
                }
                case "workspace.status" -> workspaceService.fileStatus(workspaceId(ticket, params), requiredText(params, "path"));
                case "workspace.delete" -> {
                    workspaceService.deleteFile(workspaceId(ticket, params), requiredText(params, "path"));
                    yield null;
                }
                case "agent-config.list" -> agentConfigList(ticket, params);
                case "agent-config.read" -> agentConfigRead(ticket, params);
                case "agent-config.write" -> {
                    agentConfigWrite(ticket, params);
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
            return agentConfigService.listPublicAgentFiles(text(params, "path"), worktreeId);
        }
        return agentConfigService.listWorkspaceAgentFiles(agentConfigWorkspaceId(ticket, params), text(params, "path"), worktreeId);
    }

    private Object agentConfigRead(WorkspaceFileSocketTicket ticket, JsonNode params) {
        String scope = agentConfigScope(ticket, params);
        String worktreeId = agentConfigWorktreeId(ticket, params);
        if (SCOPE_PUBLIC.equals(scope)) {
            return agentConfigService.readPublicAgentFile(requiredText(params, "path"), worktreeId);
        }
        return agentConfigService.readWorkspaceAgentFile(agentConfigWorkspaceId(ticket, params), requiredText(params, "path"), worktreeId);
    }

    private void agentConfigWrite(WorkspaceFileSocketTicket ticket, JsonNode params) {
        if (!ticket.superAdmin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
        }
        String scope = agentConfigScope(ticket, params);
        String worktreeId = agentConfigWorktreeId(ticket, params);
        if (SCOPE_PUBLIC.equals(scope)) {
            agentConfigService.writePublicAgentFile(requiredText(params, "path"), text(params, "content"), worktreeId);
            return;
        }
        agentConfigService.writeWorkspaceAgentFile(agentConfigWorkspaceId(ticket, params), requiredText(params, "path"), text(params, "content"), worktreeId);
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
}
