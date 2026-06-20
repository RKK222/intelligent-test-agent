package com.example.testagent.api.web;

import com.example.testagent.opencode.runtime.session.SessionApplicationService;
import com.example.testagent.common.api.ApiResponse;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.WorkspaceId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * Session HTTP Controller，保持请求/响应 DTO 与领域模型分离。
 */
@RestController
public class SessionController {

    private final SessionApplicationService sessionService;

    /**
     * 注入会话应用服务，Controller 仅保留协议和 DTO 转换职责。
     */
    public SessionController(SessionApplicationService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 创建 opencode 会话，traceId 从请求上下文透传到应用服务。
     */
    @PostMapping({"/api/sessions", "/api/internal/platform/opencode-runtime/sessions"})
    public ApiResponse<RuntimeDtos.SessionResponse> createSession(
            @Valid @RequestBody RuntimeDtos.CreateSessionRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.createSession(
                new WorkspaceId(request.workspaceId()), request.title(), traceId)), traceId);
    }

    /**
     * 跨工作区分页查询会话，并支持标题关键字搜索。
     */
    @GetMapping({"/api/sessions", "/api/internal/platform/opencode-runtime/sessions"})
    public ApiResponse<PageResponse<RuntimeDtos.SessionResponse>> listAllSessions(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.sessionPage(sessionService.listSessions(
                query, RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    /**
     * 查询指定工作区下的会话列表，路径 workspaceId 在边界处转换为领域 ID。
     */
    @GetMapping({"/api/workspaces/{workspaceId}/sessions", "/api/internal/platform/opencode-runtime/workspaces/{workspaceId}/sessions"})
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
    @GetMapping({"/api/sessions/{sessionId}", "/api/internal/platform/opencode-runtime/sessions/{sessionId}"})
    public ApiResponse<RuntimeDtos.SessionResponse> getSession(
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.getSession(new SessionId(sessionId))), traceId);
    }

    /**
     * 更新会话标题或置顶状态，空字段保留给应用层按局部更新规则处理。
     */
    @PatchMapping({"/api/sessions/{sessionId}", "/api/internal/platform/opencode-runtime/sessions/{sessionId}"})
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
    @DeleteMapping({"/api/sessions/{sessionId}", "/api/internal/platform/opencode-runtime/sessions/{sessionId}"})
    public ApiResponse<RuntimeDtos.SessionResponse> deleteSession(
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.archiveSession(new SessionId(sessionId), traceId)), traceId);
    }

    /**
     * 追加会话消息，角色默认和内容校验由应用服务与 DTO 校验共同兜底。
     */
    @PostMapping({"/api/sessions/{sessionId}/messages", "/api/internal/platform/opencode-runtime/sessions/{sessionId}/messages"})
    public ApiResponse<RuntimeDtos.SessionMessageResponse> appendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody RuntimeDtos.AppendMessageRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionMessageResponse.from(sessionService.appendMessage(
                new SessionId(sessionId), request.role(), request.content(), traceId)), traceId);
    }

    /**
     * 分页列出会话消息，返回值在边界层转换为稳定的 API DTO。
     */
    @GetMapping({"/api/sessions/{sessionId}/messages", "/api/internal/platform/opencode-runtime/sessions/{sessionId}/messages"})
    public ApiResponse<PageResponse<RuntimeDtos.SessionMessageResponse>> listMessages(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.messagePage(sessionService.listMessages(
                new SessionId(sessionId), RuntimeApiSupport.pageRequest(page, size))), traceId);
    }
}
