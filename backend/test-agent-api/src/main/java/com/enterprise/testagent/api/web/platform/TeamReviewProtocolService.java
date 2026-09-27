package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.team.TeamReviewModels.*;
import com.enterprise.testagent.domain.team.TeamReviewScopeStore;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.enterprise.testagent.system.management.team.SystemAdminTeamApplicationService;
import com.enterprise.testagent.workspace.TeamReviewApplicationService;
import com.enterprise.testagent.workspace.TeamReviewFileGateway;
import com.enterprise.testagent.workspace.TeamWorkspaceApplicationService;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** 审阅协议适配：控制面复用公共 Java 路由，目录和正文仅通过平台文件 WebSocket。 */
@Service
public class TeamReviewProtocolService implements TeamReviewFileGateway {
    public static final String INTERNAL_TICKET_PATH = "/api/internal/platform/workspace-management/team/review-internal/ticket";
    private final TeamReviewApplicationService review;
    private final TeamWorkspaceApplicationService workspaces;
    private final SystemAdminTeamApplicationService teams;
    private final TeamReviewScopeStore scopes;
    private final TokenSessionMarkerStore markers;
    private final BackendJavaRouteResolver routes;
    private final BackendHttpForwarder forwarder;
    private final WorkspaceFileSocketTicketStore tickets;
    private final WorkspaceFileSocketTicketService ticketService;
    private final ManagerControlSettings settings;
    private final XxlJobProperties properties;
    private final ObjectMapper mapper;
    private final AgentSessionBindingRepository bindings;
    private final RunRepository runs;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private String internalOrigin = "http://localhost:3000";

    /** 内部文件客户端和模型 Tool 使用与公共文件 handler 相同的已允许 Origin，不放宽跨域策略。 */
    @org.springframework.beans.factory.annotation.Value("${test-agent.security.cors-allowed-origins:http://localhost:3000}")
    void setInternalOrigin(String origins) {
        this.internalOrigin = origins.split(",")[0].trim();
        if ("*".equals(internalOrigin)) this.internalOrigin = "http://localhost:3000";
    }

    public TeamReviewProtocolService(@Lazy TeamReviewApplicationService review,
            TeamWorkspaceApplicationService workspaces, SystemAdminTeamApplicationService teams,
            TeamReviewScopeStore scopes, TokenSessionMarkerStore markers, BackendJavaRouteResolver routes,
            BackendHttpForwarder forwarder, WorkspaceFileSocketTicketStore tickets,
            WorkspaceFileSocketTicketService ticketService, ManagerControlSettings settings,
            XxlJobProperties properties, ObjectMapper mapper, AgentSessionBindingRepository bindings, RunRepository runs) {
        this.review = review; this.workspaces = workspaces; this.teams = teams; this.scopes = scopes;
        this.markers = markers; this.routes = routes; this.forwarder = forwarder; this.tickets = tickets;
        this.ticketService = ticketService; this.settings = settings; this.properties = properties;
        this.mapper = mapper; this.bindings = bindings; this.runs = runs;
    }

    public Scope create(AuthPrincipal principal, TeamScopeMode mode, String owner, String version, String selected) {
        var authorized = teams.authorizeScope(principal, mode, owner);
        return review.create(principal.userId().value(), markers.digest(principal.token()), authorized.scope().mode(),
                authorized.scope().ownerUserId(), version, selected, workspaces);
    }

    public Connection connection(String id, String actor, String remoteSessionId, String traceId) {
        Scope scope = review.requireScope(id, actor);
        String runId = null;
        if (remoteSessionId != null) {
            var binding = bindings.findByAgentIdAndRemoteSessionId("opencode", remoteSessionId)
                    .orElseThrow(() -> denied("当前模型会话没有平台绑定"));
            var run = runs.findLatestActiveBySessionId(binding.sessionId())
                    .filter(item -> new UserId(actor).equals(item.triggeredByUserId()))
                    .orElseThrow(() -> denied("当前审阅对话没有有效 Run"));
            runId = run.runId().value();
            if (!scopes.bindRun(id, runId)) throw denied("该审阅范围已被其它 Run 使用");
        }
        var ticket = tickets.issue(null, settings.linuxServerId().value(), null, false, false, false,
                actor, "team-review", id, runId, traceId);
        return connection(ticket);
    }

    /** 每条审阅 RPC 校验登录 marker、当前团队关系与来源映射，不能靠 UI 或历史角色放行。 */
    @Override
    public void authorize(Scope scope) {
        if (!markers.isActiveForUser(scope.actorSessionDigest(), new UserId(scope.actorUserId())))
            throw denied("审阅登录会话已失效");
        for (Source source : scope.sources()) {
            authorizeSource(scope, source);
        }
    }

    private void authorizeSource(Scope scope, Source source) {
            teams.authorizeTarget(new UserId(scope.actorUserId()), scope.scopeMode(), scope.ownerUserId(), new UserId(source.userId()));
            var current = workspaces.personalWorkspace(scope.scopeMode() == TeamScopeMode.GLOBAL,
                    scope.scopeMode() == TeamScopeMode.GLOBAL ? null : new UserId(scope.ownerUserId()), source.personalWorkspaceId());
            if (!current.workspace().versionId().value().equals(scope.versionId())
                    || !current.workspace().runtimeWorkspaceId().value().equals(source.workspaceId())
                    || !current.workspace().userId().value().equals(source.userId())
                    || !Objects.equals(current.linuxServerId(), source.linuxServerId()))
                throw denied("审阅工作区映射已变化，请刷新");
    }

    /** 来源 RPC 也复核 scope 和当前成员，防止 ticket 签发后退出登录或撤销授权。 */
    public void authorizeSourceRpc(WorkspaceFileSocketTicket ticket, String scopeId) {
        Scope scope = requireSourceScope(scopeId, ticket.userId(), ticket.supportActorSessionDigest());
        if (scope.sources().stream().noneMatch(source -> source.personalWorkspaceId().equals(ticket.supportActorSessionDigest())
                && source.workspaceId().equals(ticket.workspaceId()) && source.userId().equals(ticket.supportTargetUserId())))
            throw denied("来源不在本轮审阅范围");
    }

    private Scope requireSourceScope(String id, String actor, String personalId) {
        Scope scope = scopes.find(id).filter(value -> value.actorUserId().equals(actor) && value.expiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> denied("审阅范围已失效"));
        if (!markers.isActiveForUser(scope.actorSessionDigest(), new UserId(actor))) throw denied("审阅登录会话已失效");
        Source source = scope.sources().stream().filter(value -> value.personalWorkspaceId().equals(personalId)).findFirst()
                .orElseThrow(() -> denied("来源不在本轮审阅范围"));
        authorizeSource(scope, source);
        return scope;
    }

    public Object rpc(WorkspaceFileSocketTicket ticket, String operation, com.fasterxml.jackson.databind.JsonNode params, String traceId) {
        Scope scope = review.requireScope(ticket.scope(), ticket.userId());
        if (ticket.worktreeId() != null) {
            var run = runs.findById(new RunId(ticket.worktreeId()))
                    .filter(value -> !value.status().isTerminal() && new UserId(ticket.userId()).equals(value.triggeredByUserId()))
                    .orElseThrow(() -> denied("审阅 Run 已结束"));
        }
        String path = params.path("path").asText("");
        return switch (operation) {
            case "team.review.list" -> review.list(scope, path, traceId);
            case "team.review.search" -> review.search(scope, path, params.path("query").asText(""),
                    params.has("remainingDirectories") ? mapper.convertValue(params.path("remainingDirectories"),
                            new TypeReference<List<String>>() { }) : List.of(), traceId);
            case "team.review.read" -> review.read(scope, path, params.path("contentVersion").asText(), params.path("offset").asLong(0), traceId);
            default -> throw denied("审阅通道仅允许目录和只读分片读取");
        };
    }

    @Override
    public List<File> list(Scope scope, Source source, String path, String traceId) {
        return mapper.convertValue(remote(scope, source, "workspace.review.list", Map.of("path", path), traceId), new TypeReference<>() { });
    }

    @Override
    public Object read(Scope scope, Source source, String path, String version, long offset, String traceId) {
        return remote(scope, source, "workspace.review.read", Map.of("path", path, "contentVersion", version, "offset", offset), traceId);
    }

    public Connection sourceTicket(String scopeId, String personalId, String traceId) {
        Scope scope = scopes.find(scopeId).orElseThrow(() -> denied("审阅范围不存在"));
        scope = requireSourceScope(scopeId, scope.actorUserId(), personalId);
        Source source = scope.sources().stream().filter(value -> value.personalWorkspaceId().equals(personalId))
                .findFirst().orElseThrow(() -> denied("成员不在审阅范围"));
        var backend = routes.requireBackend(source.linuxServerId());
        if (!routes.isCurrent(backend.backendProcessId())) throw denied("ticket 必须在成员权威 Java 签发");
        var ticket = tickets.issueTeamReadOnly(source.workspaceId(), source.linuxServerId(), scope.actorUserId(),
                source.userId(), scope.scopeMode().name(), scope.ownerUserId(), source.personalWorkspaceId(), traceId);
        ticketService.authorizeTeamWorkspaceRpc(ticket, new WorkspaceId(source.workspaceId()));
        return connection(ticket);
    }

    private Object remote(Scope scope, Source source, String op, Map<String, Object> params, String traceId) {
        var backend = routes.requireBackend(source.linuxServerId());
        Connection connection = routes.isCurrent(backend.backendProcessId())
                ? sourceTicket(scope.id(), source.personalWorkspaceId(), traceId)
                : forwarder.forwardSystemTyped(backend, INTERNAL_TICKET_PATH,
                new InternalTicketRequest(scope.id(), source.personalWorkspaceId()),
                new TypeReference<com.enterprise.testagent.common.api.ApiResponse<Connection>>() { },
                traceId, properties.getAccessToken()).data();
        CompletableFuture<String> reply = new CompletableFuture<>();
        StringBuilder message = new StringBuilder();
        WebSocket socket = null;
        try {
            URI uri = URI.create(connection.baseUrl().replaceFirst("^http", "ws") + connection.webSocketUrl());
            socket = client.newWebSocketBuilder().header("Origin", connection.origin()).buildAsync(uri, new WebSocket.Listener() {
                public void onOpen(WebSocket ws) { ws.request(1); }
                public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
                    if (message.length() + text.length() > 8 * 1024 * 1024) {
                        reply.completeExceptionally(denied("成员文件报文超过预算")); ws.abort(); return null;
                    }
                    message.append(text);
                    if (last) reply.complete(message.toString());
                    ws.request(1); return null;
                }
                public void onError(WebSocket ws, Throwable error) { reply.completeExceptionally(error); }
                public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                    reply.completeExceptionally(new PlatformException(ErrorCode.CONFLICT, "成员文件连接已关闭")); return null;
                }
            }).get(10, TimeUnit.SECONDS);
            var payload = new java.util.LinkedHashMap<>(params);
            payload.put("workspaceId", source.workspaceId());
            payload.put("reviewScopeId", scope.id());
            socket.sendText(mapper.writeValueAsString(Map.of("id", "review", "op", op, "params", payload)), true).join();
            return decodeReply(mapper.readTree(reply.get(60, TimeUnit.SECONDS)));
        } catch (PlatformException exception) { throw exception; }
        catch (Exception exception) { throw new PlatformException(ErrorCode.CONFLICT, "成员文件通道暂时不可用"); }
        finally { if (socket != null) socket.abort(); }
    }

    /** 复用文件 RPC 的顶层错误信封；鉴权错误必须保留错误码，不能当作空结果降级。 */
    Object decodeReply(com.fasterxml.jackson.databind.JsonNode reply) {
        if (reply == null || !"review".equals(reply.path("id").asText()))
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "成员文件通道返回了无效报文");
        if ("error".equals(reply.path("type").asText())) {
            ErrorCode code;
            try { code = ErrorCode.valueOf(reply.path("code").asText()); }
            catch (IllegalArgumentException exception) { code = ErrorCode.INTERNAL_ERROR; }
            // 不向审阅者转发来源节点的物理路径或任意异常细节。
            throw new PlatformException(code, "成员文件读取失败，请刷新或确认成员工作区可用");
        }
        if (!"result".equals(reply.path("type").asText()) || !reply.hasNonNull("data"))
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "成员文件通道返回了无效报文");
        return mapper.convertValue(reply.path("data"), Object.class);
    }

    private Connection connection(WorkspaceFileSocketTicket ticket) {
        return new Connection(settings.listenUrl(), "/api/internal/platform/workspace-management/file/ws?ticket=" + ticket.ticket(), ticket.expiresAt(), internalOrigin);
    }
    private PlatformException denied(String message) { return new PlatformException(ErrorCode.FORBIDDEN, message); }
    public record Connection(String baseUrl, String webSocketUrl, java.time.Instant expiresAt, String origin) { }
    public record InternalTicketRequest(String scopeId, String personalWorkspaceId) { }
}
