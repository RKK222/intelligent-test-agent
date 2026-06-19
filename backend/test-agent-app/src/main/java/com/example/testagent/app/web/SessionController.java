package com.example.testagent.app.web;

import com.example.testagent.app.session.SessionApplicationService;
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

    public SessionController(SessionApplicationService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/api/sessions")
    public ApiResponse<RuntimeDtos.SessionResponse> createSession(
            @Valid @RequestBody RuntimeDtos.CreateSessionRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.createSession(
                new WorkspaceId(request.workspaceId()), request.title(), traceId)), traceId);
    }

    @GetMapping("/api/sessions")
    public ApiResponse<PageResponse<RuntimeDtos.SessionResponse>> listAllSessions(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.sessionPage(sessionService.listSessions(
                query, RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    @GetMapping("/api/workspaces/{workspaceId}/sessions")
    public ApiResponse<PageResponse<RuntimeDtos.SessionResponse>> listSessions(
            @PathVariable String workspaceId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.sessionPage(sessionService.listSessions(
                new WorkspaceId(workspaceId), RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    @GetMapping("/api/sessions/{sessionId}")
    public ApiResponse<RuntimeDtos.SessionResponse> getSession(
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.getSession(new SessionId(sessionId))), traceId);
    }

    @PatchMapping("/api/sessions/{sessionId}")
    public ApiResponse<RuntimeDtos.SessionResponse> updateSession(
            @PathVariable String sessionId,
            @RequestBody RuntimeDtos.UpdateSessionRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.updateSession(
                new SessionId(sessionId), request.title(), request.pinned(), traceId)), traceId);
    }

    @DeleteMapping("/api/sessions/{sessionId}")
    public ApiResponse<RuntimeDtos.SessionResponse> deleteSession(
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(sessionService.archiveSession(new SessionId(sessionId), traceId)), traceId);
    }

    @PostMapping("/api/sessions/{sessionId}/messages")
    public ApiResponse<RuntimeDtos.SessionMessageResponse> appendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody RuntimeDtos.AppendMessageRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.SessionMessageResponse.from(sessionService.appendMessage(
                new SessionId(sessionId), request.role(), request.content(), traceId)), traceId);
    }

    @GetMapping("/api/sessions/{sessionId}/messages")
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
