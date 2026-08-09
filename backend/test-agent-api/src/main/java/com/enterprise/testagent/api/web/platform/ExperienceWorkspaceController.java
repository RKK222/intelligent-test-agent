package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.workspace.ExperienceWorkspaceApplicationService;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 平台体验工作区入口。请求不接收路径、服务器或 Workspace ID，全部依据当前用户 READY 进程和服务端配置解析。
 */
@RestController
public class ExperienceWorkspaceController {

    private final ExperienceWorkspaceApplicationService experienceWorkspaceService;
    private final UserOpencodeProcessAssignmentService processAssignmentService;
    private final ManagedWorkspacePathResolver pathResolver;

    public ExperienceWorkspaceController(
            ExperienceWorkspaceApplicationService experienceWorkspaceService,
            UserOpencodeProcessAssignmentService processAssignmentService,
            ManagedWorkspacePathResolver pathResolver) {
        this.experienceWorkspaceService = Objects.requireNonNull(
                experienceWorkspaceService, "experienceWorkspaceService must not be null");
        this.processAssignmentService = Objects.requireNonNull(
                processAssignmentService, "processAssignmentService must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
    }

    /** 打开当前用户进程所在后端服务器的共享体验目录。 */
    @PostMapping("/api/internal/platform/workspace-management/workspaces/experience/open")
    public ApiResponse<RuntimeDtos.WorkspaceResponse> open(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        var principal = AuthWebSupport.getAuthPrincipal(exchange);
        UserOpencodeProcessAssignment assignment = processAssignmentService.requireReadyProcess(
                principal.userId(), "opencode", traceId);
        var workspace = experienceWorkspaceService.open(
                principal.userId(), assignment.linuxServerId(), traceId);
        return ApiResponse.ok(RuntimeDtos.WorkspaceResponse.from(workspace, pathResolver), traceId);
    }
}
