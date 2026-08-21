package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.workspace.ApplicationAutomationReferenceService;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 自动化引用应用级 API；成员可读，只有 APP_ADMIN/SUPER_ADMIN 可以改变共享配置。 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management/applications/{appId}/automation-reference-repositories")
public class AutomationReferenceRepositoryController {

    private final ApplicationAutomationReferenceService service;

    public AutomationReferenceRepositoryController(ApplicationAutomationReferenceService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping
    public Mono<ApiResponse<Object>> list(@PathVariable String appId, ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.list(
                appId, principal.userId(), isPrivileged(principal)));
    }

    @GetMapping("/{repositoryId}/status")
    public Mono<ApiResponse<Object>> status(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.status(
                appId, repositoryId, principal.userId(), isPrivileged(principal)));
    }

    @GetMapping("/{repositoryId}/branches")
    public Mono<ApiResponse<Object>> branches(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.branches(
                appId, repositoryId, principal.userId(), isPrivileged(principal)));
    }

    @GetMapping("/{repositoryId}/tree")
    public Mono<ApiResponse<Object>> tree(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestParam String branch,
            @RequestParam(defaultValue = "") String path,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.tree(
                appId, repositoryId, branch, path, principal.userId(), isPrivileged(principal)));
    }

    @PutMapping("/{repositoryId}/configuration")
    public Mono<ApiResponse<Object>> configure(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestBody AutomationReferenceRepositoryDtos.ConfigureRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireAdmin(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.configure(
                appId,
                repositoryId,
                request.branch(),
                request.directoryPath(),
                request.description(),
                request.merge(),
                request.expectedGeneration(),
                request.operationId(),
                principal.userId(),
                isSuperAdmin(principal),
                traceId));
    }

    @PostMapping("/{repositoryId}/synchronize")
    public Mono<ApiResponse<Object>> synchronize(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestBody AutomationReferenceRepositoryDtos.SynchronizeRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireAdmin(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.synchronize(
                appId,
                repositoryId,
                request.expectedGeneration(),
                request.operationId(),
                principal.userId(),
                isSuperAdmin(principal),
                traceId));
    }

    @PostMapping("/{repositoryId}/verify")
    public Mono<ApiResponse<Object>> verify(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestBody AutomationReferenceRepositoryDtos.GenerationRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireAdmin(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.verify(
                appId, repositoryId, request.expectedGeneration(), principal.userId(),
                isSuperAdmin(principal), traceId));
    }

    @PostMapping("/{repositoryId}/terminate")
    public Mono<ApiResponse<Object>> terminate(
            @PathVariable String appId,
            @PathVariable String repositoryId,
            @RequestBody AutomationReferenceRepositoryDtos.GenerationRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireAdmin(exchange);
        return RuntimeApiSupport.blockingObjectResponse(exchange, traceId -> service.terminate(
                appId, repositoryId, request.expectedGeneration(), principal.userId(),
                isSuperAdmin(principal), traceId));
    }

    private AuthPrincipal requireAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
    }

    private boolean isPrivileged(AuthPrincipal principal) {
        return isSuperAdmin(principal);
    }

    private boolean isSuperAdmin(AuthPrincipal principal) {
        return AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN);
    }
}
