package com.icbc.testagent.api.web.agent;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.runtime.OpencodeRuntimeApplicationService;
import com.icbc.testagent.common.api.ApiResponse;
import java.util.Map;
import java.util.function.Function;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Agent 侧 opencode 标准代理入口，按 /api/internal/agent/{agentId} 分发运行时。
 */
@RestController
public class AgentOpencodeRuntimeController {

    private final OpencodeRuntimeApplicationService runtimeService;

    /**
     * 注入运行态应用服务，所有 agent 兼容路径均在应用层完成转发。
     */
    public AgentOpencodeRuntimeController(OpencodeRuntimeApplicationService runtimeService) {
        this.runtimeService = runtimeService;
    }

    /**
     * 查询可用 agent，兼容 opencode 原始路径。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/api/agent"
    })
    public Mono<ApiResponse<Object>> listAgents(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.listAgents(workspaceId, traceId));
    }

    /**
     * 查询模型列表，workspaceId 可选以支持默认运行节点。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/api/model"
    })
    public Mono<ApiResponse<Object>> listModels(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.listModels(workspaceId, traceId));
    }

    /**
     * 查询 provider 列表，返回体保持 opencode 原始结构。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/api/provider"
    })
    public Mono<ApiResponse<Object>> listProviders(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.listProviders(workspaceId, traceId));
    }

    /**
     * 查询命令列表，用于 Web IDE 命令面板。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/api/command"
    })
    public Mono<ApiResponse<Object>> listCommands(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.listCommands(workspaceId, traceId));
    }

    /**
     * 查询引用列表，透传 opencode reference API。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/api/reference"
    })
    public Mono<ApiResponse<Object>> listReferences(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.listReferences(workspaceId, traceId));
    }

    /**
     * 查询 runtime 健康状态，兼容 opencode Web App 原始 status 路径。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/api/status"
    })
    public Mono<ApiResponse<Object>> runtimeStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.runtimeStatus(workspaceId, traceId));
    }

    /**
     * 列出文件系统目录，路径解析和 workspace 路由由应用服务负责。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/file"
    })
    public Mono<ApiResponse<Object>> fsList(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String path,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.fsList(workspaceId, path, traceId));
    }

    /**
     * 通过 opencode 文件搜索接口查找文件。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/find/file"
    })
    public Mono<ApiResponse<Object>> fsFind(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String query,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.fsFind(workspaceId, query, traceId));
    }

    /**
     * 读取文件内容，必须经后端代理而不是前端直连 opencode。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/file/content"
    })
    public Mono<ApiResponse<Object>> fsRead(
            @RequestParam(required = false) String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.fsRead(workspaceId, path, traceId));
    }

    /**
     * 查询版本控制状态，返回 opencode runtime 原始响应。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/vcs/status"
    })
    public Mono<ApiResponse<Object>> vcsStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.vcsStatus(workspaceId, traceId));
    }

    /**
     * 查询版本控制 diff，mode/context 只做透传不在 Controller 中解释。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/vcs/diff"
    })
    public Mono<ApiResponse<Object>> vcsDiff(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Integer context,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.vcsDiff(workspaceId, mode, context, traceId));
    }

    /**
     * 查询 LSP 状态，供前端判断语言服务可用性。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/lsp"
    })
    public Mono<ApiResponse<Object>> lspStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.lspStatus(workspaceId, traceId));
    }

    /**
     * 查询 MCP 服务状态。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/mcp"
    })
    public Mono<ApiResponse<Object>> mcpStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.mcpStatus(workspaceId, traceId));
    }

    /**
     * 查询 MCP 资源列表，兼容 opencode experimental resource 路径。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/experimental/resource"
    })
    public Mono<ApiResponse<Object>> mcpResources(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.mcpResources(workspaceId, traceId));
    }

    /**
     * 查询 MCP 工具列表，provider/model 参数用于 opencode runtime 侧过滤。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/experimental/tool",
            "/api/internal/agent/{agentId}/experimental/tool/ids"
    })
    public Mono<ApiResponse<Object>> mcpTools(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.mcpTools(workspaceId, provider, model, traceId));
    }

    /**
     * 读取 opencode 配置，前端只访问平台代理路径。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/global/config"
    })
    public Mono<ApiResponse<Object>> getConfig(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.getConfig(workspaceId, traceId));
    }

    /**
     * 更新 opencode 配置，响应仍由统一 ApiResponse 包装。
     */
    @PatchMapping({
            "/api/internal/agent/{agentId}/global/config"
    })
    public Mono<ApiResponse<Object>> updateConfig(
            @RequestParam(required = false) String workspaceId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.updateConfig(workspaceId, body, traceId));
    }

    /**
     * 触发 opencode 全局 dispose，供设置页重载运行态。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/global/dispose"
    })
    public Mono<ApiResponse<Object>> disposeGlobal(ServerWebExchange exchange) {
        return agentResponse(exchange, runtimeService::disposeGlobal);
    }

    /**
     * 查询 provider auth 状态。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/provider/auth"
    })
    public Mono<ApiResponse<Object>> listProviderAuth(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.listProviderAuth(workspaceId, traceId));
    }

    /**
     * 发起 provider OAuth。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/provider/{providerId}/oauth/authorize"
    })
    public Mono<ApiResponse<Object>> authorizeProviderOAuth(
            @PathVariable String providerId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.authorizeProviderOAuth(providerId, body, traceId));
    }

    /**
     * 完成 provider OAuth 回调。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/provider/{providerId}/oauth/callback"
    })
    public Mono<ApiResponse<Object>> completeProviderOAuth(
            @PathVariable String providerId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.completeProviderOAuth(providerId, body, traceId));
    }

    /**
     * 写入 provider auth secret，secret 仅透传给运行态。
     */
    @PutMapping({
            "/api/internal/agent/{agentId}/auth/{providerId}"
    })
    public Mono<ApiResponse<Object>> setProviderAuth(
            @PathVariable String providerId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.setProviderAuth(providerId, body, traceId));
    }

    /**
     * 删除 provider auth secret。
     */
    @DeleteMapping({
            "/api/internal/agent/{agentId}/auth/{providerId}"
    })
    public Mono<ApiResponse<Object>> removeProviderAuth(@PathVariable String providerId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.removeProviderAuth(providerId, traceId));
    }

    /**
     * 查询 experimental worktree。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/experimental/worktree"
    })
    public Mono<ApiResponse<Object>> listWorktrees(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.listWorktrees(workspaceId, traceId));
    }

    /**
     * 创建 experimental worktree。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/experimental/worktree"
    })
    public Mono<ApiResponse<Object>> createWorktree(
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.createWorktree(body, traceId));
    }

    /**
     * 删除 experimental worktree。
     */
    @DeleteMapping({
            "/api/internal/agent/{agentId}/experimental/worktree"
    })
    public Mono<ApiResponse<Object>> removeWorktree(
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.removeWorktree(body, traceId));
    }

    /**
     * 重置 experimental worktree。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/experimental/worktree/reset"
    })
    public Mono<ApiResponse<Object>> resetWorktree(
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.resetWorktree(body, traceId));
    }

    /**
     * 查询会话子会话列表，支撑 Web IDE 的会话树。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/children"
    })
    public Mono<ApiResponse<Object>> sessionChildren(@PathVariable String sessionId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.sessionChildren(sessionId, traceId));
    }

    /**
     * 查询会话 todo 状态，保持与 opencode session todo API 一致。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/todo"
    })
    public Mono<ApiResponse<Object>> sessionTodo(@PathVariable String sessionId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.sessionTodo(sessionId, traceId));
    }

    /**
     * 查询会话 diff，messageId 可选用于定位特定消息产生的变更。
     */
    @GetMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/diff"
    })
    public Mono<ApiResponse<Object>> sessionDiff(
            @PathVariable String sessionId,
            @RequestParam(required = false) String messageId,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.sessionDiff(sessionId, messageId, traceId));
    }

    /**
     * 中止 opencode 会话当前执行。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/abort"
    })
    public Mono<ApiResponse<Object>> abortSession(@PathVariable String sessionId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.abortSession(sessionId, traceId));
    }

    /**
     * fork 会话，body 原样交给运行态应用服务适配 opencode。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/fork"
    })
    public Mono<ApiResponse<Object>> forkSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.forkSession(sessionId, body, traceId));
    }

    /**
     * 压缩会话上下文，对外仍使用统一 ApiResponse。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/summarize"
    })
    public Mono<ApiResponse<Object>> compactSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.compactSession(sessionId, body, traceId));
    }

    /**
     * 回滚会话变更，body 中的 part/message 信息由应用层转换。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/revert"
    })
    public Mono<ApiResponse<Object>> revertSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.revertSession(sessionId, body, traceId));
    }

    /**
     * 撤销回滚操作，维持与 revert 对称的兼容路径。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/unrevert"
    })
    public Mono<ApiResponse<Object>> unrevertSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.unrevertSession(sessionId, body, traceId));
    }

    /**
     * 向会话发送命令消息，具体请求体由 runtime service 规范化。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/command"
    })
    public Mono<ApiResponse<Object>> commandSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.commandSession(sessionId, body, traceId));
    }

    /**
     * 向会话发送 shell 命令，仍通过后端代理执行安全边界控制。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/shell"
    })
    public Mono<ApiResponse<Object>> shellSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.shellSession(sessionId, body, traceId));
    }

    /**
     * 创建会话分享链接。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/share"
    })
    public Mono<ApiResponse<Object>> shareSession(@PathVariable String sessionId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.shareSession(sessionId, traceId));
    }

    /**
     * 取消会话分享。
     */
    @DeleteMapping({
            "/api/internal/agent/{agentId}/session/{sessionId}/share"
    })
    public Mono<ApiResponse<Object>> unshareSession(@PathVariable String sessionId, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.unshareSession(sessionId, traceId));
    }

    /**
     * 兼容 opencode 原始 permission 查询路径，sessionId 从 query 读取。
     */
    @GetMapping("/api/internal/agent/{agentId}/permission")
    public Mono<ApiResponse<Object>> listPermissionsByOpencodePath(
            @RequestParam String sessionId,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.listPermissions(sessionId, traceId));
    }

    /**
     * 兼容 opencode 原始 permission reply 路径。
     */
    @PostMapping("/api/internal/agent/{agentId}/permission/{requestId}/reply")
    public Mono<ApiResponse<Object>> replyPermissionByOpencodePath(
            @RequestParam String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.replyPermission(sessionId, requestId, body, traceId));
    }

    /**
     * 兼容 opencode 原始 question 查询路径，sessionId 从 query 读取。
     */
    @GetMapping("/api/internal/agent/{agentId}/question")
    public Mono<ApiResponse<Object>> listQuestionsByOpencodePath(
            @RequestParam String sessionId,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.listQuestions(sessionId, traceId));
    }

    /**
     * 兼容 opencode 原始 question reply 路径。
     */
    @PostMapping("/api/internal/agent/{agentId}/question/{requestId}/reply")
    public Mono<ApiResponse<Object>> replyQuestionByOpencodePath(
            @RequestParam String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.replyQuestion(sessionId, requestId, body, traceId));
    }

    /**
     * 兼容 opencode 原始 question reject 路径。
     */
    @PostMapping("/api/internal/agent/{agentId}/question/{requestId}/reject")
    public Mono<ApiResponse<Object>> rejectQuestionByOpencodePath(
            @RequestParam String sessionId,
            @PathVariable String requestId,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.rejectQuestion(sessionId, requestId, traceId));
    }

    /**
     * 发起 MCP auth。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/mcp/{name}/auth"
    })
    public Mono<ApiResponse<Object>> startMcpAuth(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.startMcpAuth(name, body, traceId));
    }

    /**
     * 完成 MCP auth callback。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/mcp/{name}/auth/callback"
    })
    public Mono<ApiResponse<Object>> completeMcpAuth(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.completeMcpAuth(name, body, traceId));
    }

    /**
     * 执行 MCP auth authenticate。
     */
    @PostMapping({
            "/api/internal/agent/{agentId}/mcp/{name}/auth/authenticate"
    })
    public Mono<ApiResponse<Object>> authenticateMcp(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.authenticateMcp(name, body, traceId));
    }

    /**
     * 删除 MCP auth。
     */
    @DeleteMapping({
            "/api/internal/agent/{agentId}/mcp/{name}/auth"
    })
    public Mono<ApiResponse<Object>> removeMcpAuth(@PathVariable String name, ServerWebExchange exchange) {
        return agentResponse(exchange, traceId -> runtimeService.removeMcpAuth(name, traceId));
    }

    /**
     * 从 URL 提取 agentId 并设置到 runtime service 的同步调用上下文。
     */
    private Mono<ApiResponse<Object>> agentResponse(ServerWebExchange exchange, Function<String, Object> action) {
        String agentId = agentId(exchange);
        return RuntimeApiSupport.blockingObjectResponse(
                exchange,
                traceId -> runtimeService.withAgent(agentId, optionalUserId(exchange), () -> action.apply(traceId)));
    }

    /**
     * 用户 token 鉴权成功时传入 UserId；static token 或本地放行时返回空保持旧固定节点兼容。
     */
    private UserId optionalUserId(ServerWebExchange exchange) {
        return AuthWebSupport.getOptionalAuthPrincipal(exchange)
                .map(principal -> principal.userId())
                .orElse(null);
    }

    /**
     * 解析 /api/internal/agent/{agentId}/... 中的 agentId，缺失时交由 registry 默认 opencode。
     */
    private String agentId(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String prefix = "/api/internal/agent/";
        int start = path.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        int agentStart = start + prefix.length();
        int agentEnd = path.indexOf('/', agentStart);
        return agentEnd < 0 ? path.substring(agentStart) : path.substring(agentStart, agentEnd);
    }
}
