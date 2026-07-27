package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceGitToolTokenService;
import com.enterprise.testagent.workspace.ConversationWorkspaceGitApplicationService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * OpenCode 公共 Tool 的受控工作区 Git 入口。
 *
 * <p>该入口只接受进程启动时注入的专用签名凭据，不能复用为普通用户登录 Token；当前工作区
 * 由远端 session 反查，避免 Tool 传入任意 workspace ID。</p>
 */
@RestController
public class WorkspaceGitToolController {

    private final WorkspaceGitToolTokenService tokenService;
    private final ConversationWorkspaceGitApplicationService workspaceGitService;

    public WorkspaceGitToolController(
            WorkspaceGitToolTokenService tokenService,
            ConversationWorkspaceGitApplicationService workspaceGitService) {
        this.tokenService = tokenService;
        this.workspaceGitService = workspaceGitService;
    }

    /** 执行公共 Tool 发起的单个工作区 Git 动作。 */
    @PostMapping(WorkspaceGitToolTokenService.ENDPOINT_PATH)
    public ApiResponse<Object> execute(
            @RequestBody WorkspaceGitToolRequest request,
            ServerWebExchange exchange) {
        var principal = tokenService.authenticate(
                exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        return ApiResponse.ok(workspaceGitService.execute(
                request.sessionId(),
                request.action(),
                request.files(),
                request.message(),
                request.path(),
                request.resolution(),
                request.content(),
                request.expectedApplicationHead(),
                principal.userId(),
                principal.roles(),
                RuntimeApiSupport.traceId(exchange)), RuntimeApiSupport.traceId(exchange));
    }

    /** Tool 协议保持可选字段，具体动作所需参数由业务服务做一致校验。 */
    public record WorkspaceGitToolRequest(
            String sessionId,
            String action,
            List<String> files,
            String message,
            String path,
            String resolution,
            String content,
            String expectedApplicationHead) {
    }
}
