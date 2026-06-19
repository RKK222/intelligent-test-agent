package com.example.testagent.api.web;

import com.example.testagent.opencode.runtime.runtime.OpencodeRuntimeApplicationService;
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

    @GetMapping({
            "/api/agents",
            "/api/internal/platform/opencode-runtime/agents",
            "/api/internal/agent/opencode/api/agent"
    })
    public Mono<ApiResponse<Object>> listAgents(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listAgents(workspaceId, traceId));
    }

    @GetMapping({
            "/api/models",
            "/api/internal/platform/opencode-runtime/models",
            "/api/internal/agent/opencode/api/model"
    })
    public Mono<ApiResponse<Object>> listModels(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listModels(workspaceId, traceId));
    }

    @GetMapping({
            "/api/providers",
            "/api/internal/platform/opencode-runtime/providers",
            "/api/internal/agent/opencode/api/provider"
    })
    public Mono<ApiResponse<Object>> listProviders(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listProviders(workspaceId, traceId));
    }

    @GetMapping({
            "/api/commands",
            "/api/internal/platform/opencode-runtime/commands",
            "/api/internal/agent/opencode/api/command"
    })
    public Mono<ApiResponse<Object>> listCommands(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listCommands(workspaceId, traceId));
    }

    @GetMapping({
            "/api/references",
            "/api/internal/platform/opencode-runtime/references",
            "/api/internal/agent/opencode/api/reference"
    })
    public Mono<ApiResponse<Object>> listReferences(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listReferences(workspaceId, traceId));
    }

    @GetMapping({
            "/api/fs/list",
            "/api/internal/platform/opencode-runtime/fs/list",
            "/api/internal/agent/opencode/file"
    })
    public Mono<ApiResponse<Object>> fsList(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String path,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.fsList(workspaceId, path, traceId));
    }

    @GetMapping({
            "/api/fs/find",
            "/api/internal/platform/opencode-runtime/fs/find",
            "/api/internal/agent/opencode/find/file"
    })
    public Mono<ApiResponse<Object>> fsFind(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String query,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.fsFind(workspaceId, query, traceId));
    }

    @GetMapping({
            "/api/fs/read",
            "/api/internal/platform/opencode-runtime/fs/read",
            "/api/internal/agent/opencode/file/content"
    })
    public Mono<ApiResponse<Object>> fsRead(
            @RequestParam(required = false) String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.fsRead(workspaceId, path, traceId));
    }

    @GetMapping({
            "/api/vcs/status",
            "/api/internal/platform/opencode-runtime/vcs/status",
            "/api/internal/agent/opencode/vcs/status"
    })
    public Mono<ApiResponse<Object>> vcsStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.vcsStatus(workspaceId, traceId));
    }

    @GetMapping({
            "/api/vcs/diff",
            "/api/internal/platform/opencode-runtime/vcs/diff",
            "/api/internal/agent/opencode/vcs/diff"
    })
    public Mono<ApiResponse<Object>> vcsDiff(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Integer context,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.vcsDiff(workspaceId, mode, context, traceId));
    }

    @GetMapping({
            "/api/lsp/status",
            "/api/internal/platform/opencode-runtime/lsp/status",
            "/api/internal/agent/opencode/lsp"
    })
    public Mono<ApiResponse<Object>> lspStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.lspStatus(workspaceId, traceId));
    }

    @GetMapping({
            "/api/mcp/status",
            "/api/internal/platform/opencode-runtime/mcp/status",
            "/api/internal/agent/opencode/mcp"
    })
    public Mono<ApiResponse<Object>> mcpStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.mcpStatus(workspaceId, traceId));
    }

    @GetMapping({
            "/api/mcp/resources",
            "/api/internal/platform/opencode-runtime/mcp/resources",
            "/api/internal/agent/opencode/experimental/resource"
    })
    public Mono<ApiResponse<Object>> mcpResources(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.mcpResources(workspaceId, traceId));
    }

    @GetMapping({
            "/api/mcp/tools",
            "/api/internal/platform/opencode-runtime/mcp/tools",
            "/api/internal/agent/opencode/experimental/tool",
            "/api/internal/agent/opencode/experimental/tool/ids"
    })
    public Mono<ApiResponse<Object>> mcpTools(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.mcpTools(workspaceId, provider, model, traceId));
    }

    @GetMapping({
            "/api/sessions/{sessionId}/children",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/children",
            "/api/internal/agent/opencode/session/{sessionId}/children"
    })
    public Mono<ApiResponse<Object>> sessionChildren(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.sessionChildren(sessionId, traceId));
    }

    @GetMapping({
            "/api/sessions/{sessionId}/todo",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/todo",
            "/api/internal/agent/opencode/session/{sessionId}/todo"
    })
    public Mono<ApiResponse<Object>> sessionTodo(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.sessionTodo(sessionId, traceId));
    }

    @GetMapping({
            "/api/sessions/{sessionId}/diff",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/diff",
            "/api/internal/agent/opencode/session/{sessionId}/diff"
    })
    public Mono<ApiResponse<Object>> sessionDiff(
            @PathVariable String sessionId,
            @RequestParam(required = false) String messageId,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.sessionDiff(sessionId, messageId, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/abort",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/abort",
            "/api/internal/agent/opencode/session/{sessionId}/abort"
    })
    public Mono<ApiResponse<Object>> abortSession(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.abortSession(sessionId, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/fork",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/fork",
            "/api/internal/agent/opencode/session/{sessionId}/fork"
    })
    public Mono<ApiResponse<Object>> forkSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.forkSession(sessionId, body, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/compact",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/compact",
            "/api/internal/agent/opencode/session/{sessionId}/summarize"
    })
    public Mono<ApiResponse<Object>> compactSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.compactSession(sessionId, body, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/revert",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/revert",
            "/api/internal/agent/opencode/session/{sessionId}/revert"
    })
    public Mono<ApiResponse<Object>> revertSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.revertSession(sessionId, body, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/unrevert",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/unrevert",
            "/api/internal/agent/opencode/session/{sessionId}/unrevert"
    })
    public Mono<ApiResponse<Object>> unrevertSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.unrevertSession(sessionId, body, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/command",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/command",
            "/api/internal/agent/opencode/session/{sessionId}/command"
    })
    public Mono<ApiResponse<Object>> commandSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.commandSession(sessionId, body, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/shell",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/shell",
            "/api/internal/agent/opencode/session/{sessionId}/shell"
    })
    public Mono<ApiResponse<Object>> shellSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.shellSession(sessionId, body, traceId));
    }

    @GetMapping({
            "/api/sessions/{sessionId}/permissions",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/permissions"
    })
    public Mono<ApiResponse<Object>> listPermissions(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listPermissions(sessionId, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/permissions/{requestId}/reply",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/permissions/{requestId}/reply"
    })
    public Mono<ApiResponse<Object>> replyPermission(
            @PathVariable String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.replyPermission(sessionId, requestId, body, traceId));
    }

    @GetMapping({
            "/api/sessions/{sessionId}/questions",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/questions"
    })
    public Mono<ApiResponse<Object>> listQuestions(@PathVariable String sessionId, ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listQuestions(sessionId, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/questions/{requestId}/reply",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/questions/{requestId}/reply"
    })
    public Mono<ApiResponse<Object>> replyQuestion(
            @PathVariable String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.replyQuestion(sessionId, requestId, body, traceId));
    }

    @PostMapping({
            "/api/sessions/{sessionId}/questions/{requestId}/reject",
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/questions/{requestId}/reject"
    })
    public Mono<ApiResponse<Object>> rejectQuestion(
            @PathVariable String sessionId,
            @PathVariable String requestId,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.rejectQuestion(sessionId, requestId, traceId));
    }

    @GetMapping("/api/internal/agent/opencode/permission")
    public Mono<ApiResponse<Object>> listPermissionsByOpencodePath(
            @RequestParam String sessionId,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listPermissions(sessionId, traceId));
    }

    @PostMapping("/api/internal/agent/opencode/permission/{requestId}/reply")
    public Mono<ApiResponse<Object>> replyPermissionByOpencodePath(
            @RequestParam String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.replyPermission(sessionId, requestId, body, traceId));
    }

    @GetMapping("/api/internal/agent/opencode/question")
    public Mono<ApiResponse<Object>> listQuestionsByOpencodePath(
            @RequestParam String sessionId,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.listQuestions(sessionId, traceId));
    }

    @PostMapping("/api/internal/agent/opencode/question/{requestId}/reply")
    public Mono<ApiResponse<Object>> replyQuestionByOpencodePath(
            @RequestParam String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return response(exchange, traceId -> runtimeService.replyQuestion(sessionId, requestId, body, traceId));
    }

    @PostMapping("/api/internal/agent/opencode/question/{requestId}/reject")
    public Mono<ApiResponse<Object>> rejectQuestionByOpencodePath(
            @RequestParam String sessionId,
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
