package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.api.web.common.SideQuestionDtos;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.runtime.OpencodeRuntimeApplicationService;
import com.enterprise.testagent.opencode.runtime.runtime.SideQuestionStreamingApplicationService;
import com.enterprise.testagent.common.api.ApiResponse;
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
 * 平台侧 opencode runtime API 入口，只承载旧 /api 和 internal platform 路径。
 */
@RestController
public class PlatformOpencodeRuntimeController {

    private final OpencodeRuntimeApplicationService runtimeService;
    private final SideQuestionStreamingApplicationService sideQuestionStreamingService;

    /**
     * 注入运行态应用服务，所有 opencode 兼容路径均在应用层完成转发。
     */
    public PlatformOpencodeRuntimeController(
            OpencodeRuntimeApplicationService runtimeService,
            SideQuestionStreamingApplicationService sideQuestionStreamingService) {
        this.runtimeService = runtimeService;
        this.sideQuestionStreamingService = sideQuestionStreamingService;
    }

    /**
     * 查询可用 agent，兼容项目自有 API 和内部平台 API。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/agents",
    })
    public Mono<ApiResponse<Object>> listAgents(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listAgents(workspaceId, traceId));
    }

    /**
     * 查询模型列表，workspaceId 可选以支持默认运行节点。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/models",
    })
    public Mono<ApiResponse<Object>> listModels(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listModels(workspaceId, traceId));
    }

    /**
     * 查询 provider 列表，返回体保持 opencode 原始结构。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/providers",
    })
    public Mono<ApiResponse<Object>> listProviders(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listProviders(workspaceId, traceId));
    }

    /**
     * 查询命令列表，用于 Web IDE 命令面板。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/commands",
    })
    public Mono<ApiResponse<Object>> listCommands(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listCommands(workspaceId, traceId));
    }

    /**
     * 查询引用列表，透传 opencode reference API。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/references",
    })
    public Mono<ApiResponse<Object>> listReferences(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listReferences(workspaceId, traceId));
    }

    /**
     * 查询 runtime 健康状态，供前端兼容 opencode Web App status 请求。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/status",
    })
    public Mono<ApiResponse<Object>> runtimeStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.runtimeStatus(workspaceId, traceId));
    }

    /**
     * 列出文件系统目录，路径解析和 workspace 路由由应用服务负责。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/fs/list",
    })
    public Mono<ApiResponse<Object>> fsList(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String path,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.fsList(workspaceId, path, traceId));
    }

    /**
     * 通过 opencode 文件搜索接口查找文件。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/fs/find",
    })
    public Mono<ApiResponse<Object>> fsFind(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String query,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.fsFind(workspaceId, query, traceId));
    }

    /**
     * 读取文件内容，必须经后端代理而不是前端直连 opencode。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/fs/read",
    })
    public Mono<ApiResponse<Object>> fsRead(
            @RequestParam(required = false) String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.fsRead(workspaceId, path, traceId));
    }

    /**
     * 查询版本控制状态，返回 opencode runtime 原始响应。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/vcs/status",
    })
    public Mono<ApiResponse<Object>> vcsStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.vcsStatus(workspaceId, traceId));
    }

    /**
     * 查询版本控制 diff，mode/context 只做透传不在 Controller 中解释。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/vcs/diff",
    })
    public Mono<ApiResponse<Object>> vcsDiff(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Integer context,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.vcsDiff(workspaceId, mode, context, traceId));
    }

    /**
     * 查询 LSP 状态，供前端判断语言服务可用性。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/lsp/status",
    })
    public Mono<ApiResponse<Object>> lspStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.lspStatus(workspaceId, traceId));
    }

    /**
     * 查询 MCP 服务状态。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/mcp/status",
    })
    public Mono<ApiResponse<Object>> mcpStatus(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.mcpStatus(workspaceId, traceId));
    }

    /**
     * 查询 MCP 资源列表，兼容 opencode experimental resource 路径。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/mcp/resources",
    })
    public Mono<ApiResponse<Object>> mcpResources(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.mcpResources(workspaceId, traceId));
    }

    /**
     * 查询 MCP 工具列表，provider/model 参数用于 opencode runtime 侧过滤。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/mcp/tools",
    })
    public Mono<ApiResponse<Object>> mcpTools(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.mcpTools(workspaceId, provider, model, traceId));
    }

    /**
     * 读取 opencode 合并后的有效配置，确保公共配置目录中的 Provider 白名单可供模型目录过滤。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/config",
    })
    public Mono<ApiResponse<Object>> getConfig(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.getEffectiveConfig(workspaceId, traceId));
    }

    /**
     * 更新 opencode 配置，响应仍由统一 ApiResponse 包装。
     */
    @PatchMapping({
            "/api/internal/platform/opencode-runtime/config",
    })
    public Mono<ApiResponse<Object>> updateConfig(
            @RequestParam(required = false) String workspaceId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.updateConfig(workspaceId, body, traceId));
    }

    /**
     * 触发 opencode 全局 dispose，供设置页重载运行态。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/global/dispose",
    })
    public Mono<ApiResponse<Object>> disposeGlobal(ServerWebExchange exchange) {
        return platformResponse(exchange, runtimeService::disposeGlobal);
    }

    /**
     * 查询 provider auth 状态。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/provider/auth",
    })
    public Mono<ApiResponse<Object>> listProviderAuth(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listProviderAuth(workspaceId, traceId));
    }

    /**
     * 发起 provider OAuth。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/provider/{providerId}/oauth/authorize",
    })
    public Mono<ApiResponse<Object>> authorizeProviderOAuth(
            @PathVariable String providerId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.authorizeProviderOAuth(providerId, body, traceId));
    }

    /**
     * 完成 provider OAuth 回调。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/provider/{providerId}/oauth/callback",
    })
    public Mono<ApiResponse<Object>> completeProviderOAuth(
            @PathVariable String providerId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.completeProviderOAuth(providerId, body, traceId));
    }

    /**
     * 写入 provider auth secret，secret 仅透传给运行态。
     */
    @PutMapping({
            "/api/internal/platform/opencode-runtime/auth/{providerId}",
    })
    public Mono<ApiResponse<Object>> setProviderAuth(
            @PathVariable String providerId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.setProviderAuth(providerId, body, traceId));
    }

    /**
     * 删除 provider auth secret。
     */
    @DeleteMapping({
            "/api/internal/platform/opencode-runtime/auth/{providerId}",
    })
    public Mono<ApiResponse<Object>> removeProviderAuth(@PathVariable String providerId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.removeProviderAuth(providerId, traceId));
    }

    /**
     * 查询 experimental worktree。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/worktrees",
    })
    public Mono<ApiResponse<Object>> listWorktrees(@RequestParam(required = false) String workspaceId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listWorktrees(workspaceId, traceId));
    }

    /**
     * 创建 experimental worktree。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/worktrees",
    })
    public Mono<ApiResponse<Object>> createWorktree(
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.createWorktree(body, traceId));
    }

    /**
     * 删除 experimental worktree。
     */
    @DeleteMapping({
            "/api/internal/platform/opencode-runtime/worktrees",
    })
    public Mono<ApiResponse<Object>> removeWorktree(
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.removeWorktree(body, traceId));
    }

    /**
     * 重置 experimental worktree。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/worktrees/reset",
    })
    public Mono<ApiResponse<Object>> resetWorktree(
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.resetWorktree(body, traceId));
    }

    /**
     * 查询会话子会话列表，支撑 Web IDE 的会话树。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/children",
    })
    public Mono<ApiResponse<Object>> sessionChildren(@PathVariable String sessionId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.sessionChildren(sessionId, traceId));
    }

    /**
     * 查询会话 todo 状态，保持与 opencode session todo API 一致。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/todo",
    })
    public Mono<ApiResponse<Object>> sessionTodo(@PathVariable String sessionId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.sessionTodo(sessionId, traceId));
    }

    /**
     * 查询会话 diff，messageId 可选用于定位特定消息产生的变更。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/diff",
    })
    public Mono<ApiResponse<Object>> sessionDiff(
            @PathVariable String sessionId,
            @RequestParam(required = false) String messageId,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.sessionDiff(sessionId, messageId, traceId));
    }

    /**
     * 中止 opencode 会话当前执行。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/abort",
    })
    public Mono<ApiResponse<Object>> abortSession(@PathVariable String sessionId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.abortSession(sessionId, traceId));
    }

    /**
     * fork 会话，body 原样交给运行态应用服务适配 opencode。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/fork",
    })
    public Mono<ApiResponse<Object>> forkSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.forkSession(sessionId, body, traceId));
    }

    /**
     * 执行一次不写入主会话历史的宠物旁路问答，临时 fork 的生命周期由应用层负责清理。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/side-question"
    })
    public Mono<ApiResponse<Object>> sideQuestion(
            @PathVariable String sessionId,
            @jakarta.validation.Valid @RequestBody SideQuestionDtos.Request request,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> SideQuestionDtos.Response.from(runtimeService.sideQuestion(sessionId, request.toInput(), traceId)));
    }

    /**
     * 创建可通过既有 RunEvent SSE 订阅的旁路问答 Run；平台入口固定使用 opencode runtime。
     */
    @PostMapping("/api/internal/platform/opencode-runtime/sessions/{sessionId}/side-question/runs")
    public Mono<ApiResponse<Object>> startSideQuestionRun(
            @PathVariable String sessionId,
            @jakarta.validation.Valid @RequestBody SideQuestionDtos.StreamRequest request,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return RuntimeApiSupport.blockingObjectResponse(
                exchange,
                traceId -> SideQuestionDtos.StreamResponse.from(sideQuestionStreamingService.start(
                        userId,
                        "opencode",
                        new SessionId(sessionId),
                        request.question(),
                        request.messageId(),
                        request.model(),
                        traceId)));
    }

    /**
     * 创建不依赖主对话的手册问答 Run；内部 Session 从创建起即归档，回答仍通过 RunEvent SSE 输出。
     */
    @PostMapping("/api/internal/platform/opencode-runtime/manual-question/runs")
    public Mono<ApiResponse<Object>> startManualQuestionRun(
            @jakarta.validation.Valid @RequestBody SideQuestionDtos.ManualStreamRequest request,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return RuntimeApiSupport.blockingObjectResponse(
                exchange,
                traceId -> SideQuestionDtos.StreamResponse.from(sideQuestionStreamingService.startManual(
                        userId,
                        "opencode",
                        new WorkspaceId(request.workspaceId()),
                        request.question(),
                        request.model(),
                        traceId)));
    }

    /**
     * 压缩会话上下文，对外仍使用统一 ApiResponse。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/compact",
    })
    public Mono<ApiResponse<Object>> compactSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.compactSession(sessionId, body, traceId));
    }

    /**
     * 回滚会话变更，body 中的 part/message 信息由应用层转换。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/revert",
    })
    public Mono<ApiResponse<Object>> revertSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.revertSession(sessionId, body, traceId));
    }

    /**
     * 撤销回滚操作，维持与 revert 对称的兼容路径。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/unrevert",
    })
    public Mono<ApiResponse<Object>> unrevertSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.unrevertSession(sessionId, body, traceId));
    }

    /**
     * 向会话发送命令消息，具体请求体由 runtime service 规范化。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/command",
    })
    public Mono<ApiResponse<Object>> commandSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.commandSession(sessionId, body, traceId));
    }

    /**
     * 向会话发送 shell 命令，仍通过后端代理执行安全边界控制。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/shell",
    })
    public Mono<ApiResponse<Object>> shellSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.shellSession(sessionId, body, traceId));
    }

    /**
     * 创建会话分享链接。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/share",
    })
    public Mono<ApiResponse<Object>> shareSession(@PathVariable String sessionId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.shareSession(sessionId, traceId));
    }

    /**
     * 取消会话分享。
     */
    @DeleteMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/share",
    })
    public Mono<ApiResponse<Object>> unshareSession(@PathVariable String sessionId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.unshareSession(sessionId, traceId));
    }

    /**
     * 列出会话待处理权限请求。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/permissions"
    })
    public Mono<ApiResponse<Object>> listPermissions(@PathVariable String sessionId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listPermissions(sessionId, traceId));
    }

    /**
     * 回复权限请求，decision 等字段由应用层生成 opencode 兼容请求体。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/permissions/{requestId}/reply"
    })
    public Mono<ApiResponse<Object>> replyPermission(
            @PathVariable String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.replyPermission(sessionId, requestId, body, traceId));
    }

    /**
     * 列出会话待处理问题请求。
     */
    @GetMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/questions"
    })
    public Mono<ApiResponse<Object>> listQuestions(@PathVariable String sessionId, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listQuestions(sessionId, traceId));
    }

    /**
     * 回复问题请求，保留统一 traceId 以便关联运行态日志。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/questions/{requestId}/reply"
    })
    public Mono<ApiResponse<Object>> replyQuestion(
            @PathVariable String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.replyQuestion(sessionId, requestId, body, traceId));
    }

    /**
     * 拒绝问题请求。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/sessions/{sessionId}/questions/{requestId}/reject"
    })
    public Mono<ApiResponse<Object>> rejectQuestion(
            @PathVariable String sessionId,
            @PathVariable String requestId,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.rejectQuestion(sessionId, requestId, traceId));
    }

    /**
     * 兼容 opencode 原始 permission 查询路径，sessionId 从 query 读取。
     */
    public Mono<ApiResponse<Object>> listPermissionsByOpencodePath(
            @RequestParam String sessionId,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listPermissions(sessionId, traceId));
    }

    /**
     * 兼容 opencode 原始 permission reply 路径。
     */
    public Mono<ApiResponse<Object>> replyPermissionByOpencodePath(
            @RequestParam String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.replyPermission(sessionId, requestId, body, traceId));
    }

    /**
     * 兼容 opencode 原始 question 查询路径，sessionId 从 query 读取。
     */
    public Mono<ApiResponse<Object>> listQuestionsByOpencodePath(
            @RequestParam String sessionId,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.listQuestions(sessionId, traceId));
    }

    /**
     * 兼容 opencode 原始 question reply 路径。
     */
    public Mono<ApiResponse<Object>> replyQuestionByOpencodePath(
            @RequestParam String sessionId,
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.replyQuestion(sessionId, requestId, body, traceId));
    }

    /**
     * 兼容 opencode 原始 question reject 路径。
     */
    public Mono<ApiResponse<Object>> rejectQuestionByOpencodePath(
            @RequestParam String sessionId,
            @PathVariable String requestId,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.rejectQuestion(sessionId, requestId, traceId));
    }

    /**
     * 发起 MCP auth。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/mcp/{name}/auth",
    })
    public Mono<ApiResponse<Object>> startMcpAuth(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.startMcpAuth(name, body, traceId));
    }

    /**
     * 完成 MCP auth callback。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/mcp/{name}/auth/callback",
    })
    public Mono<ApiResponse<Object>> completeMcpAuth(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.completeMcpAuth(name, body, traceId));
    }

    /**
     * 执行 MCP auth authenticate。
     */
    @PostMapping({
            "/api/internal/platform/opencode-runtime/mcp/{name}/auth/authenticate",
    })
    public Mono<ApiResponse<Object>> authenticateMcp(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.authenticateMcp(name, body, traceId));
    }

    /**
     * 删除 MCP auth。
     */
    @DeleteMapping({
            "/api/internal/platform/opencode-runtime/mcp/{name}/auth",
    })
    public Mono<ApiResponse<Object>> removeMcpAuth(@PathVariable String name, ServerWebExchange exchange) {
        return platformResponse(exchange, traceId -> runtimeService.removeMcpAuth(name, traceId));
    }

    /**
     * 平台旧入口没有 agentId path，默认 opencode；有用户主体时走用户进程，无用户时固定节点 fallback。
     */
    private Mono<ApiResponse<Object>> platformResponse(ServerWebExchange exchange, Function<String, Object> action) {
        return RuntimeApiSupport.blockingObjectResponse(
                exchange,
                traceId -> runtimeService.withUser(optionalUserId(exchange), () -> action.apply(traceId)));
    }

    /**
     * 用户 token 鉴权成功时传入 UserId；static token 或本地放行时返回空保持旧固定节点兼容。
     */
    private UserId optionalUserId(ServerWebExchange exchange) {
        return AuthWebSupport.getOptionalAuthPrincipal(exchange)
                .map(principal -> principal.userId())
                .orElse(null);
    }

}
