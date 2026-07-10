package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.opencode.runtime.run.RunApplicationService;
import com.icbc.testagent.opencode.runtime.run.RunMessageRecoveryService;
import com.icbc.testagent.opencode.runtime.session.SessionApplicationService;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.event.RunEventSsePayload;
import com.icbc.testagent.event.RunEventSseStreamService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Session HTTP Controller，保持请求/响应 DTO 与领域模型分离。
 */
@RestController
public class SessionController {

    private final SessionApplicationService sessionService;
    private final RunApplicationService runService;
    private final RunMessageRecoveryService messageRecoveryService;
    private final RunEventSseStreamService eventStreamService;

    /**
     * 注入会话应用服务，Controller 仅保留协议和 DTO 转换职责。
     */
    public SessionController(SessionApplicationService sessionService) {
        this(sessionService, null, null, null);
    }

    /**
     * 注入会话与 Run 应用服务，active-run 查询用于刷新后恢复 SSE。
     */
    public SessionController(SessionApplicationService sessionService, RunApplicationService runService) {
        this(sessionService, runService, null, null);
    }

    /**
     * 注入会话、Run 与消息恢复服务，Session 历史树查询复用 Run snapshot 恢复链路。
     */
    public SessionController(
            SessionApplicationService sessionService,
            RunApplicationService runService,
            RunMessageRecoveryService messageRecoveryService) {
        this(sessionService, runService, messageRecoveryService, null);
    }

    /**
     * 注入会话、Run、消息恢复和事件回放服务，Session 历史树可补齐 durable 状态事件。
     */
    @Autowired
    public SessionController(
            SessionApplicationService sessionService,
            RunApplicationService runService,
            RunMessageRecoveryService messageRecoveryService,
            RunEventSseStreamService eventStreamService) {
        this.sessionService = sessionService;
        this.runService = runService;
        this.messageRecoveryService = messageRecoveryService;
        this.eventStreamService = eventStreamService;
    }

    /**
     * 创建 opencode 会话，traceId 从请求上下文透传到应用服务。
     */
    @PostMapping("/api/internal/platform/opencode-runtime/sessions")
    public ApiResponse<RuntimeDtos.SessionResponse> createSession(
            @Valid @RequestBody RuntimeDtos.CreateSessionRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.createSession(
                userId, new WorkspaceId(request.workspaceId()), request.title(), traceId)), traceId);
    }

    /**
     * 当前用户跨工作区分页查询历史会话，并支持标题关键字搜索。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/sessions")
    public ApiResponse<PageResponse<RuntimeDtos.SessionResponse>> listAllSessions(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(RuntimeDtos.sessionHistoryPage(sessionService.listUserSessions(
                userId, query, RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    /**
     * 查询指定工作区下的会话列表，路径 workspaceId 在边界处转换为领域 ID。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/workspaces/{workspaceId}/sessions")
    public ApiResponse<PageResponse<RuntimeDtos.SessionResponse>> listSessions(
            @PathVariable String workspaceId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.sessionPage(sessionService.listSessions(
                new WorkspaceId(workspaceId), RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    /**
     * 查询单个会话详情，归档隐藏等业务规则由应用层执行。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/sessions/{sessionId}")
    public ApiResponse<RuntimeDtos.SessionResponse> getSession(
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.getSession(new SessionId(sessionId))), traceId);
    }

    /**
     * 查询会话最近仍在执行的 Run；没有活跃 Run 时 data 返回 null。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/sessions/{sessionId}/active-run")
    public ApiResponse<RuntimeDtos.RunResponse> getActiveRun(
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        RuntimeDtos.RunResponse response = runService == null
                ? null
                : runService.findActiveRun(new SessionId(sessionId))
                        .map(RuntimeDtos.RunResponse::from)
                        .orElse(null);
        return ApiResponse.ok(response, traceId);
    }

    /**
     * 更新会话标题或置顶状态，空字段保留给应用层按局部更新规则处理。
     */
    @PatchMapping("/api/internal/platform/opencode-runtime/sessions/{sessionId}")
    public ApiResponse<RuntimeDtos.SessionResponse> updateSession(
            @PathVariable String sessionId,
            @RequestBody RuntimeDtos.UpdateSessionRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.updateSession(
                new SessionId(sessionId), request.title(), request.pinned(), traceId)), traceId);
    }

    /**
     * 软删除会话，HTTP delete 映射为应用层归档操作。
     */
    @DeleteMapping("/api/internal/platform/opencode-runtime/sessions/{sessionId}")
    public ApiResponse<RuntimeDtos.SessionResponse> deleteSession(
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(
                sessionService.archiveSession(userId, new SessionId(sessionId), traceId)), traceId);
    }

    /**
     * 追加会话消息，角色默认和内容校验由应用服务与 DTO 校验共同兜底。
     */
    @PostMapping("/api/internal/platform/opencode-runtime/sessions/{sessionId}/messages")
    public ApiResponse<RuntimeDtos.SessionMessageResponse> appendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody RuntimeDtos.AppendMessageRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(RuntimeDtos.SessionMessageResponse.from(sessionService.appendMessage(
                userId, new SessionId(sessionId), request.role(), request.content(), traceId)), traceId);
    }

    /**
     * 分页列出会话消息，返回值在边界层转换为稳定的 API DTO。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/sessions/{sessionId}/messages")
    public Mono<ApiResponse<PageResponse<RuntimeDtos.SessionMessageResponse>>> listMessages(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "true") Boolean refresh,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        // 历史消息查询会同步刷新远端快照，必须整体 offload，避免在 Reactor 事件线程调用 block()。
        return Mono.fromCallable(() -> ApiResponse.ok(RuntimeDtos.messagePage(sessionService.listMessages(
                        new SessionId(sessionId), RuntimeApiSupport.pageRequest(page, size), traceId, Boolean.TRUE.equals(refresh))), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询 Session root 下全量历史 session tree message snapshot；agent-scoped 路径是主入口。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/session-tree/messages"
    })
    public Mono<ApiResponse<RuntimeDtos.SessionTreeMessagesResponse>> getSessionTreeMessages(
            @PathVariable(name = "agentId", required = false) String agentId,
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        SessionId currentSessionId = new SessionId(sessionId);
        return Mono.fromCallable(() -> {
                    List<RunEventSsePayload> snapshotEvents = messageRecoveryService == null
                            ? List.of()
                            : (hasAgentId(agentId)
                                    ? messageRecoveryService.recoverSessionTree(agentId, currentSessionId, traceId)
                                    : messageRecoveryService.recoverSessionTree(currentSessionId, traceId))
                                    .collectList()
                                    .block(Duration.ofSeconds(30));
                    List<RunEventSsePayload> allEvents = new ArrayList<>(snapshotEvents);
                    allEvents.addAll(durableSnapshotPayloadsByRootSessionId(snapshotEvents));
                    return ApiResponse.ok(
                            RuntimeDtos.SessionTreeMessagesResponse.from(sessionId, allEvents),
                            traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<RunEventSsePayload> durableSnapshotPayloadsByRootSessionId(List<RunEventSsePayload> snapshotEvents) {
        if (eventStreamService == null || snapshotEvents == null || snapshotEvents.isEmpty()) {
            return List.of();
        }
        Set<String> rootSessionIds = new LinkedHashSet<>();
        for (RunEventSsePayload event : snapshotEvents) {
            Map<String, Object> payload = event.payload();
            Object rootSessionId = payload == null ? null : payload.get("rootSessionId");
            if (rootSessionId instanceof String value && !value.isBlank()) {
                rootSessionIds.add(value);
            }
        }
        List<RunEventSsePayload> events = new ArrayList<>();
        for (String rootSessionId : rootSessionIds) {
            List<RunEventSsePayload> durable =
                    eventStreamService.snapshotDurablePayloadsByRootSessionId(rootSessionId, 0L, 100);
            if (durable != null) {
                events.addAll(durable);
            }
        }
        return List.copyOf(events);
    }

    private boolean hasAgentId(String agentId) {
        return agentId != null && !agentId.isBlank();
    }
}
