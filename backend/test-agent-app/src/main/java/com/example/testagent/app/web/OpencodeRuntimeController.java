package com.example.testagent.app.web;

import com.example.testagent.app.runtime.OpencodeRuntimeApplicationService;
import com.example.testagent.common.api.ApiResponse;
import java.util.Map;
import java.util.function.Function;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Phase 11 opencode Web App 运行态 API 入口，所有 opencode 调用均委托 runtime application service。
 */
@RestController
public class OpencodeRuntimeController {

    private final OpencodeRuntimeApplicationService runtimeService;

    public OpencodeRuntimeController(OpencodeRuntimeApplicationService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @GetMapping("/api/agents")
    public Mono<ApiResponse<Object>> listAgents(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listAgents(workspaceId, traceId));
    }

    @GetMapping("/api/models")
    public Mono<ApiResponse<Object>> listModels(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listModels(workspaceId, traceId));
    }

    @GetMapping("/api/providers")
    public Mono<ApiResponse<Object>> listProviders(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listProviders(workspaceId, traceId));
    }

    @GetMapping("/api/commands")
    public Mono<ApiResponse<Object>> listCommands(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listCommands(workspaceId, traceId));
    }

    @GetMapping("/api/references")
    public Mono<ApiResponse<Object>> listReferences(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listReferences(workspaceId, traceId));
    }

    @GetMapping("/api/fs/list")
    public Mono<ApiResponse<Object>> fsList(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String path,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.fsList(workspaceId, path, traceId));
    }

    @GetMapping("/api/fs/find")
    public Mono<ApiResponse<Object>> fsFind(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String query,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.fsFind(workspaceId, query, traceId));
    }

    @GetMapping("/api/fs/read")
    public Mono<ApiResponse<Object>> fsRead(
            @RequestParam(required = false) String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.fsRead(workspaceId, path, traceId));
    }

    @GetMapping("/api/vcs/status")
    public Mono<ApiResponse<Object>> vcsStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.vcsStatus(workspaceId, traceId));
    }

    @GetMapping("/api/vcs/diff")
    public Mono<ApiResponse<Object>> vcsDiff(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Integer context,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.vcsDiff(workspaceId, mode, context, traceId));
    }

    @GetMapping("/api/lsp/status")
    public Mono<ApiResponse<Object>> lspStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.lspStatus(workspaceId, traceId));
    }

    @GetMapping("/api/mcp/status")
    public Mono<ApiResponse<Object>> mcpStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.mcpStatus(workspaceId, traceId));
    }

    @GetMapping("/api/mcp/resources")
    public Mono<ApiResponse<Object>> mcpResources(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.mcpResources(workspaceId, traceId));
    }

    @GetMapping("/api/mcp/tools")
    public Mono<ApiResponse<Object>> mcpTools(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.mcpTools(workspaceId, provider, model, traceId));
    }

    @GetMapping("/api/sessions/{sessionId}/children")
    public Mono<ApiResponse<Object>> sessionChildren(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.sessionChildren(sessionId, traceId));
    }

    @GetMapping("/api/sessions/{sessionId}/todo")
    public Mono<ApiResponse<Object>> sessionTodo(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.sessionTodo(sessionId, traceId));
    }

    @GetMapping("/api/sessions/{sessionId}/diff")
    public Mono<ApiResponse<Object>> sessionDiff(
            @PathVariable String sessionId,
            @RequestParam(required = false) String messageId,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.sessionDiff(sessionId, messageId, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/abort")
    public Mono<ApiResponse<Object>> abortSession(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.abortSession(sessionId, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/fork")
    public Mono<ApiResponse<Object>> forkSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.forkSession(sessionId, body, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/compact")
    public Mono<ApiResponse<Object>> compactSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.compactSession(sessionId, body, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/revert")
    public Mono<ApiResponse<Object>> revertSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.revertSession(sessionId, body, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/unrevert")
    public Mono<ApiResponse<Object>> unrevertSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.unrevertSession(sessionId, body, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/command")
    public Mono<ApiResponse<Object>> commandSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.commandSession(sessionId, body, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/shell")
    public Mono<ApiResponse<Object>> shellSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.shellSession(sessionId, body, traceId));
    }

    @GetMapping("/api/sessions/{sessionId}/permissions")
    public Mono<ApiResponse<Object>> listPermissions(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listPermissions(sessionId, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/permissions/{requestId}/reply")
    public Mono<ApiResponse<Object>> replyPermission(
            @PathVariable String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.replyPermission(sessionId, requestId, body, traceId));
    }

    @GetMapping("/api/sessions/{sessionId}/questions")
    public Mono<ApiResponse<Object>> listQuestions(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listQuestions(sessionId, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/questions/{requestId}/reply")
    public Mono<ApiResponse<Object>> replyQuestion(
            @PathVariable String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.replyQuestion(sessionId, requestId, body, traceId));
    }

    @PostMapping("/api/sessions/{sessionId}/questions/{requestId}/reject")
    public Mono<ApiResponse<Object>> rejectQuestion(
            @PathVariable String sessionId,
            @PathVariable String requestId,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.rejectQuestion(sessionId, requestId, traceId));
    }

    private Mono<ApiResponse<Object>> response(ServerWebExchange exchange, Function<String, Object> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
