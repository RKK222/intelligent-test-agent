package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementApplicationService;
import com.icbc.testagent.domain.user.UserId;
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

    public ConfigurationManagementController(ConfigurationManagementApplicationService service) {
        this.service = service;
    }

    @GetMapping("/applications")
    public ApiResponse<Object> listApplications(
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
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
        requireAdmin(exchange);
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

    @PostMapping("/repositories")
    public ApiResponse<Object> createRepository(
            @RequestBody ConfigurationManagementDtos.CreateRepositoryRequest request,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.createRepository(request.gitUrl(), request.name(), request.standard()));
    }

    @PatchMapping("/repositories/{repositoryId}")
    public ApiResponse<Object> updateRepository(
            @PathVariable String repositoryId,
            @RequestBody ConfigurationManagementDtos.UpdateRepositoryRequest request,
            ServerWebExchange exchange) {
        requireAdmin(exchange);
        return ok(exchange, service.updateRepository(repositoryId, request.name(), request.standard()));
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
        requireAdmin(exchange);
        return ok(exchange, service.createWorkspace(
                appId,
                request.repositoryId(),
                request.branch(),
                request.directoryPath(),
                request.workspaceName()));
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
        return ok(exchange, service.addSshKey(userId, request.name(), request.privateKey()));
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

    private ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
