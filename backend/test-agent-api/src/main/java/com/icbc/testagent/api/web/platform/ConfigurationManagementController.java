package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.RsaKeyService;
import com.icbc.testagent.configuration.management.ConfigurationManagementApplicationService;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.workspace.ManagedWorkspaceApplicationService;
import java.net.URI;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 应用配置管理 HTTP 入口，仅做鉴权、参数转换和统一响应包装。
 */
@RestController
@RequestMapping("/api/internal/platform/configuration-management")
public class ConfigurationManagementController {

    private static final String APP_ADMIN = "APP_ADMIN";

    private final ConfigurationManagementApplicationService service;
    private final ManagedWorkspaceApplicationService workspaceService;
    private final UserOpencodeProcessAssignmentService processAssignmentService;
    private final RsaKeyService rsaKeyService;

    public ConfigurationManagementController(
            ConfigurationManagementApplicationService service,
            ManagedWorkspaceApplicationService workspaceService,
            UserOpencodeProcessAssignmentService processAssignmentService,
            RsaKeyService rsaKeyService) {
        this.service = service;
        this.workspaceService = workspaceService;
        this.processAssignmentService = processAssignmentService;
        this.rsaKeyService = rsaKeyService;
    }

    /**
     * 返回 RSA 公钥（SPKI Base64），供前端混合加密 SSH 私钥时使用。无需鉴权。
     */
    @GetMapping("/ssh-key/public-key")
    public ApiResponse<Object> getSshKeyPublicKey(ServerWebExchange exchange) {
        return ok(exchange, Map.of("publicKey", rsaKeyService.getPublicKeyBase64()));
    }

    @GetMapping("/applications")
    public ApiResponse<Object> listApplications(
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        return ok(exchange, service.listApplications(enabled));
    }

    @GetMapping("/applications/{appId}/members")
    public ApiResponse<Object> listMembers(@PathVariable String appId, ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.listMembers(appId));
    }

    @PostMapping("/applications/{appId}/members")
    public ApiResponse<Object> addMember(
            @PathVariable String appId,
            @RequestBody ConfigurationManagementDtos.AddMemberRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        String targetUserId = request.userId() == null ? "" : request.userId();
        if (!principal.userId().value().equals(targetUserId) && !AuthWebSupport.hasRole(principal, APP_ADMIN)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "普通用户只能将自己加入应用",
                    Map.of("appId", appId, "currentUserId", principal.userId().value(), "targetUserId", targetUserId));
        }
        return ok(exchange, service.addMember(appId, request.userId()));
    }

    @DeleteMapping("/applications/{appId}/members/{userId}")
    public ApiResponse<Object> removeMember(
            @PathVariable String appId,
            @PathVariable String userId,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        service.removeMember(appId, userId);
        return ok(exchange, null);
    }

    @GetMapping("/users")
    public ApiResponse<Object> searchUsers(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.searchUsers(keyword, RuntimeApiSupport.pageRequest(page, size)));
    }

    @GetMapping("/repositories")
    public ApiResponse<Object> listRepositories(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.listRepositories(RuntimeApiSupport.pageRequest(page, size)));
    }

    @GetMapping("/repository-types")
    public ApiResponse<Object> listRepositoryTypes(ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.listRepositoryTypes());
    }

    @GetMapping("/repository-deployment-options")
    public ApiResponse<Object> repositoryDeploymentOptions(ServerWebExchange exchange) {
        UserId userId = requireAdmin(exchange);
        return ok(exchange, service.repositoryDeploymentOptions(userId));
    }

    @PostMapping("/repositories")
    public ApiResponse<Object> createRepository(
            @RequestBody ConfigurationManagementDtos.CreateRepositoryRequest request,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.createRepository(
                request.gitUrl(),
                request.name(),
                request.englishName(),
                request.standard(),
                request.repositoryType(),
                request.deploymentMode()));
    }

    @PatchMapping("/repositories/{repositoryId}")
    public ApiResponse<Object> updateRepository(
            @PathVariable String repositoryId,
            @RequestBody ConfigurationManagementDtos.UpdateRepositoryRequest request,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.updateRepository(repositoryId, request.name(), request.englishName(), request.standard()));
    }

    @GetMapping("/applications/{appId}/repositories")
    public ApiResponse<Object> listApplicationRepositories(@PathVariable String appId, ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.listApplicationRepositories(appId));
    }

    @PostMapping("/applications/{appId}/repositories")
    public ApiResponse<Object> linkRepositoryToApplication(
            @PathVariable String appId,
            @RequestBody ConfigurationManagementDtos.LinkRepositoryRequest request,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.linkRepositoryToApplication(appId, request.repositoryId()));
    }

    @DeleteMapping("/applications/{appId}/repositories/{repositoryId}")
    public ApiResponse<Object> unlinkRepositoryFromApplication(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        service.unlinkRepositoryFromApplication(appId, repositoryId);
        return ok(exchange, null);
    }

    @GetMapping("/repositories/{repositoryId}/applications")
    public ApiResponse<Object> listRepositoryApplications(@PathVariable String repositoryId, ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.listRepositoryApplications(repositoryId));
    }

    @PostMapping("/repositories/{repositoryId}/applications")
    public ApiResponse<Object> linkApplicationToRepository(
            @PathVariable String repositoryId,
            @RequestBody ConfigurationManagementDtos.LinkApplicationRequest request,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.linkApplicationToRepository(repositoryId, request.appId()));
    }

    @DeleteMapping("/repositories/{repositoryId}/applications/{appId}")
    public ApiResponse<Object> unlinkApplicationFromRepository(
            @PathVariable String repositoryId,
            @PathVariable String appId,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        service.unlinkApplicationFromRepository(repositoryId, appId);
        return ok(exchange, null);
    }

    @GetMapping("/repositories/{repositoryId}/branches")
    public ApiResponse<Object> listBranches(@PathVariable String repositoryId, ServerWebExchange exchange) {
        UserId userId = requireAdmin(exchange);
        return ok(exchange, service.listBranches(repositoryId, userId));
    }

    @GetMapping("/repositories/{repositoryId}/directories")
    public ApiResponse<Object> listDirectories(
            @PathVariable String repositoryId,
            @RequestParam String branch,
            ServerWebExchange exchange) {
        UserId userId = requireAdmin(exchange);
        return ok(exchange, service.listDirectories(repositoryId, branch, userId));
    }

    @GetMapping("/applications/{appId}/repositories/{repositoryId}/tree")
    public ApiResponse<Object> listRepositoryTree(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestParam String branch,
            ServerWebExchange exchange) {
        UserId userId = requireAdmin(exchange);
        return ok(exchange, service.listRepositoryTree(appId, repositoryId, branch, userId));
    }

    @GetMapping("/applications/{appId}/workspaces")
    public ApiResponse<Object> listWorkspaces(@PathVariable String appId, ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.listWorkspaces(appId));
    }

    @PostMapping("/applications/{appId}/workspaces")
    public ApiResponse<Object> createWorkspace(
            @PathVariable String appId,
            @RequestBody ConfigurationManagementDtos.CreateApplicationWorkspaceRequest request,
            ServerWebExchange exchange) {
        UserId userId = requireAdmin(exchange);
        // 异步创建：立即返回 operationId，前端通过轮询获取进度
        return ok(exchange, workspaceService.createWorkspaceAccepted(
                appId,
                request.repositoryId(),
                request.branch(),
                request.directoryPath(),
                request.workspaceName(),
                request.directoryNew(),
                request.version(),
                request.operationId(),
                userId,
                agentLinuxServerId(exchange, userId),
                RuntimeApiSupport.traceId(exchange)));
    }

    @GetMapping("/workspace-create-operations/{operationId}")
    public ApiResponse<Object> getWorkspaceCreateOperation(@PathVariable String operationId, ServerWebExchange exchange) {
        UserId userId = requireAdmin(exchange);
        return ok(exchange, workspaceService.getWorkspaceCreateOperation(operationId, userId));
    }

    @PatchMapping("/applications/{appId}/workspaces/{workspaceId}")
    public ApiResponse<Object> renameWorkspace(
            @PathVariable String appId,
            @PathVariable String workspaceId,
            @RequestBody ConfigurationManagementDtos.RenameApplicationWorkspaceRequest request,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.renameWorkspace(appId, workspaceId, request.workspaceName()));
    }

    @DeleteMapping("/applications/{appId}/workspaces/{workspaceId}")
    public ApiResponse<Object> deleteWorkspace(
            @PathVariable String appId,
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        service.deleteWorkspace(appId, workspaceId);
        return ok(exchange, null);
    }

    @GetMapping("/personal/ssh-keys")
    public ApiResponse<Object> listPersonalSshKeys(ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ok(exchange, service.listSshKeys(userId));
    }

    @PostMapping("/personal/ssh-keys")
    public ApiResponse<Object> addPersonalSshKey(
            @RequestBody ConfigurationManagementDtos.AddSshKeyRequest request,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ok(exchange, service.addSshKey(
                userId,
                request.name(),
                request.encryptedPrivateKey(),
                request.encryptedAesKey(),
                request.encryptionNonce(),
                request.fingerprint()));
    }

    @DeleteMapping("/personal/ssh-keys/{sshKeyId}")
    public ApiResponse<Object> deletePersonalSshKey(@PathVariable String sshKeyId, ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        service.deleteSshKey(userId, sshKeyId);
        return ok(exchange, null);
    }

    private UserId requireAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, APP_ADMIN).userId();
    }

    private String agentLinuxServerId(ServerWebExchange exchange, UserId userId) {
        UserOpencodeProcessAssignment assignment = processAssignmentService.requireReadyProcess(
                userId,
                "opencode",
                RuntimeApiSupport.traceId(exchange));
        if (assignment.linuxServerId() != null) {
            return assignment.linuxServerId();
        }
        try {
            return URI.create(assignment.node().baseUrl()).getHost();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
